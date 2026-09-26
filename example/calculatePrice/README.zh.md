# calculatePrice — 按需计算价格

[English](README.md) | **中文**

jsonbom 的基础示例（入门场景）。展示的技术点：

1. 客户端查询 BOM 驱动下游取数，未请求的字段不产生任何取数动作；
2. 用 `@BomMapping` 描述返回模型与 BOM 路径的对应关系；
3. `transformBom` 把面向响应的请求 BOM 重排为面向模型的 BOM；
4. 派生字段通过 `Bom.merge` 把它缺失的依赖补进上游 BOM；
5. 用冷流 `Publisher` 注册多个模型，映射器只订阅被请求字段需要的模型；
6. `ValueHandler` 实现字段级自定义逻辑；
7. `@PublisherLog` AOP 切面打印响应式方法的入参与返回值。

## 场景

价格接口返回以下字段的部分或全部：

| 字段 | 来源 | 说明 |
|------|------|------|
| `originalPrice` | 商品模型 | 折扣前价格 |
| `discount` | 促销模型 | 该商品的促销折扣合计 |
| `finalPrice` | 计算得出 | `max(originalPrice - discount, 0)` |
| `priceText` | 计算得出 | `finalPrice` 格式化后的文本，如 `￥196.60` |

客户端只请求需要的字段；各数据源按收到的 BOM 键决定填充哪些属性，未请求的字段不会产生任何取数动作。

## 项目结构

```
src/main/java/io/github/hanbernate/jsonbom/
├── example/
│   ├── AppConfig.java                # Spring 配置（ComponentScan + AspectJ 代理）
│   ├── PriceOrchestrator.java        # 入口：请求 BOM -> PriceModel
│   ├── DiscountOrchestrator.java     # 折扣汇总
│   └── repository/
│       ├── GoodsRepository.java      # 模拟商品数据源
│       └── PromotionRepository.java  # 模拟促销数据源
└── core/
    ├── PublisherLog.java             # @PublisherLog 注解
    └── PublisherLogAdvice.java       # AOP 日志：打印响应式方法的入参与返回值
```

## 返回模型

`PriceModel` 描述每个字段的数据来源：

```java
@Data
public static class PriceModel {
    @BomMapping("goods/originalPrice")
    private BigDecimal originalPrice;

    @BomMapping("discount")
    private BigDecimal discount;

    @BomMapping("finalPrice")
    BigDecimal finalPrice;

    @BomMapping(value = "finalPrice", valueHandler = PriceTextValueHandler.class)
    String priceText;
}
```

`priceText` 与 `finalPrice` 读取同一个 BOM 节点，前者通过 ValueHandler 格式化为文本。

## 请求与返回

客户端请求（声明需要的 `PriceModel` 字段）：

```json
{
  "originalPrice": "",
  "discount": "",
  "finalPrice": ""
}
```

返回（数值由示例中的模拟数据源产生）：

```json
{
  "originalPrice": 199.00,
  "discount": 2.40,
  "finalPrice": 196.60,
  "priceText": "￥196.60"
}
```

只请求部分字段时，例如 `{ "discount": "" }`，返回中只有 `discount` 有值，其余字段为 `null`，且不会触发商品查询。

## 实现细节

1. `PriceOrchestrator.getPriceModel(Mono<Bom> bom, Mono<Long> goodsId)` 先把面向响应的 BOM 重排为面向模型的 BOM：

   ```java
   Mono<Bom> upstreamBom = bom.map(this::upstreamBom).cache();
   ```

   `upstreamBom` 调用 `jsonBomMapper.getBomAdapter().transformBom(targetBom, PriceModel.class)`，按 `PriceModel` 的 `@BomMapping` 路径重组 BOM：

   | 请求的路径 | 上游 BOM |
   |------------|----------|
   | `originalPrice` | `{"goods":{"originalPrice":""}}` |
   | `discount` | `{"discount":""}` |
   | `finalPrice` / `priceText` | `{"finalPrice":"", "discount":"", "goods":{"originalPrice":""}}` |
   | `originalPrice` + `discount` | `{"goods":{"originalPrice":""}, "discount":""}` |
   | `originalPrice` + `finalPrice` | `{"goods":{"originalPrice":""}, "discount":"", "finalPrice":""}` |
   | `discount` + `finalPrice` | 同上 |
   | 三个字段全部请求 | 同上 |

2. 请求了 `finalPrice` 时，派生值同时依赖 `discount` 与 `goods/originalPrice`，编排层把它们合并进上游 BOM：

   ```java
   r.merge("discount", BomOrValue.EMPTY);

   Bom goodsBom = new Bom();
   goodsBom.merge("originalPrice", BomOrValue.EMPTY);
   r.merge("goods", new BomOrValue(null, goodsBom));
   ```

   `Bom.merge` 对已存在的键不做覆盖，并对嵌套 BOM 递归合并，所以客户端已请求的 `originalPrice` 不会被改写。商品 BOM 中始终只包含 `originalPrice`：折扣统一走促销模型查询，不再通过商品表获取。

   请求 BOM 同时是向上游传递的载体：`transformBom(targetBom, PriceModel.class)` 把客户端请求重排为模型路径，派生字段（`finalPrice`）通过 `Bom.merge` 把它缺失的依赖（`discount`、`goods/originalPrice`）补进该 BOM，各数据源只收到属于自己的子 BOM（`upstreamBom.map(b -> b.getBom("goods"))`），因此下游严格按请求字段取数。

3. `upstreamBom` 使用 `.cache()` 缓存：它同时被商品查询与最终价计算消费。

4. 各 `Publisher` 以与上游 BOM 键一致的模型名注册：

   ```java
   Mono<GoodsRepository.Goods> goods = goodsRepository.findById(upstreamBom.map(b -> b.getBom("goods")), goodsId);
   Mono<BigDecimal> discount = discountOrchestrator.calculateDiscount(goodsId);
   Mono<BigDecimal> finalPrice = goods.zipWith(discount, this::calculateFinalPrice);

   Map<String, Publisher<?>> models = new HashMap<>();
   models.put("goods", goods);
   models.put("discount", discount);
   models.put("finalPrice", finalPrice);
   return (Mono<PriceModel>) (Publisher<?>) jsonBomMapper.map(bom, PriceModel.class, models);
   ```

   这些 Publisher 是冷流：映射器只订阅被请求字段需要的模型，未请求的模型不会执行。

5. `GoodsRepository.findById` 与 `PromotionRepository.findByGoodsIdId` 模拟数据库访问，并严格按收到的 BOM 取数，键存在才填充对应字段：

   ```java
   if (b.containsKey("originalPrice")) {
       goods.setOriginalPrice(new BigDecimal("199.00"));
   }
   ```

6. `DiscountOrchestrator.calculateDiscount` 构造只包含 `discount` 的最小促销 BOM，并把该商品的促销折扣汇总为一个 `BigDecimal`。

7. `PriceTextValueHandler` 把 `finalPrice` 转换为展示文案，值缺失时返回 `null`：

   ```java
   public String apply(Object model, String bomValue) {
       if (null == model) {
           return null;
       }
       return "￥" + model.toString();
   }
   ```

8. `getPriceModel` 与 `calculateDiscount` 上的 `@PublisherLog` 触发 `PublisherLogAdvice`：切面先缓存 `Mono`/`Flux` 类型的入参与返回值（日志不会引入额外订阅），再以 DEBUG 级别输出一行 JSON，包含 `class`、`method`、`args`、`result`：

   ```json
   {"class":"io.github.hanbernate.jsonbom.example.PriceOrchestrator","method":"getPriceModel","args":{"bom":{"finalPrice":""},"goodsId":42},"result":{"originalPrice":199.00,"discount":2.40,"finalPrice":196.60,"priceText":"￥196.60"}}
   ```

## 测试

| 测试 | 覆盖点 |
|------|--------|
| `PriceOrchestratorTest.GetPriceModelTest` | 全量/部分字段请求，走真实的 `ReactorJsonBomMapper` |
| `PriceOrchestratorTest.UpstreamBomTest` | 7 种字段组合下上游 BOM 的裁剪结果 |
| `PriceOrchestratorTest.CalculateFinalPriceTest` | 减法与最小为 0 的兜底 |
| `PriceOrchestratorTest.PriceTextValueHandlerTest` | `￥` 前缀、整数、null 处理 |
| `PublisherLogAdviceTest` | `Mono` / 普通对象 / `null` / 空 Publisher 入参与返回值的 AOP 日志 |

运行：

```
./gradlew :calculatePrice:test
```

## 相关示例

- [enumMap](../enumMap/README.zh.md) — 枚举驱动的模型注册
- [queryDbOnDemand](../queryDbOnDemand/README.zh.md) — 按需 SQL 列裁剪
- [mcpServer](../mcpServer/README.zh.md) — 面向 MCP 工具的 BOM 查询 Schema
