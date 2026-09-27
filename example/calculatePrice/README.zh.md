# calculatePrice — 按需计算价格

[English](README.md) | **中文**

jsonbom 的基础示例（入门场景）。它以 Spring Boot WebFlux 应用的形式运行，因此整个按需取数流程都可以通过 HTTP 来体验。展示的技术点：

1. 客户端查询 BOM 驱动下游取数，未请求的字段不产生任何取数动作；
2. 用 `@BomMapping` 描述返回模型与 BOM 路径的对应关系；
3. `transformBom` 把面向响应的请求 BOM 重排为面向模型的 BOM；
4. 派生字段通过 `Bom.merge` 把它缺失的依赖补进上游 BOM；
5. 用冷流 `Publisher` 注册多个模型，映射器只订阅被请求字段需要的模型；
6. `ValueHandler` 实现字段级自定义逻辑；
7. `@PublisherLog` AOP 切面打印响应式方法的入参与返回值；
8. 用 **Jackson 3**（`tools.jackson` + `Jackson3Deserializer`）解析请求 BOM；
9. 用 **`WebClient`** 走一次真实 HTTP 跳转调用接口 —— 集成测试把请求 BOM POST 给 `PriceController`，并断言按需返回的结果。

## 场景

价格接口返回以下字段的部分或全部：

| 字段 | 来源 | 说明 |
|------|------|------|
| `originalPrice` | 商品模型 | 折扣前价格 |
| `discount` | 促销模型 | 该商品的促销折扣合计 |
| `finalPrice` | 计算得出 | `max(originalPrice - discount, 0)` |
| `priceText` | 计算得出 | `finalPrice` 格式化后的文本，如 `￥196.60` |

客户端只请求需要的字段。BOM 作为请求体提交，编排层把重排后的子 BOM 交给各 Repository，由它们只填充被请求的字段。

## 项目结构

```
src/main/java/io/github/hanbernate/jsonbom/
├── example/
│   ├── PriceApplication.java         # @SpringBootApplication（扫描库与示例包）
│   ├── AppConfig.java                # AspectJ 自动代理 + Jackson 3 JsonMapper / JsonBomMapper Bean
│   ├── PriceOrchestrator.java        # 入口：请求 BOM -> PriceModel
│   ├── DiscountOrchestrator.java     # 折扣汇总
│   ├── web/
│   │   └── PriceController.java      # POST /price/{goodsId} —— 接收 JSON BOM
│   └── repository/
│       ├── GoodsRepository.java      # @Repository，固定商品数据
│       └── PromotionRepository.java  # @Repository，固定促销数据
└── core/
    ├── PublisherLog.java             # @PublisherLog 注解
    └── PublisherLogAdvice.java       # AOP 日志：打印响应式方法的入参与返回值
```

```
src/main/resources/
└── application.yml                   # server.port=18082
```

## HTTP 入口

`PriceController` 接收 `text/plain` 形式的紧凑 JSON BOM，返回 `application/json`：

```java
@PostMapping(value = "/price/{goodsId}",
        consumes = MediaType.TEXT_PLAIN_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
public Mono<PriceModel> price(@PathVariable Long goodsId,
        @RequestBody(required = false) String body) {
    Bom bom = (null == body || body.isBlank()) ? new Bom() : jsonMapper.readValue(body, Bom.class);
    return priceOrchestrator.getPriceModel(Mono.just(bom), Mono.just(goodsId));
}
```

BOM 通过注入的 Jackson 3 `JsonMapper` Bean 解析；HTTP 请求/响应体本身仍由 Spring WebFlux 使用 Jackson 2 进行（反）序列化，因此两个 Jackson 版本共存：

* **Jackson 3** 负责 `Bom` —— 传输中的紧凑 BOM JSON，由 `AppConfig` 提供的 `JsonMapper` Bean 读取。
* **Jackson 2** 负责 `String` 类型的 BOM 请求体与 JSON 响应的 POJO 编解码。

## 数据来源

`GoodsRepository.findById` 与 `PromotionRepository.findByGoodsIdId` 是进程内的 `@Repository` Bean，持有固定数据，使示例保持自包含。它们只填充收到的子 BOM 中存在的字段，模拟一次按列裁剪的查询：

```java
public Mono<Goods> findById(Mono<Bom> bom, Mono<Long> goodsId) {
    return Mono.zip(bom, goodsId, (b, id) -> {
        Goods goods = new Goods(0L, "", BigDecimal.ZERO, BigDecimal.ZERO);
        if (b.containsKey("goodsId"))       goods.setGoodsId(id);
        if (b.containsKey("goodsName"))     goods.setGoodsName("Sample Goods");
        if (b.containsKey("originalPrice")) goods.setOriginalPrice(new BigDecimal("199.00"));
        if (b.containsKey("discount"))      goods.setDiscount(new BigDecimal("0.8"));
        return goods;
    });
}
```

真实的 HTTP 跳转放在测试里：`PriceEndpointIntegrationTest` 在固定端口启动应用，由 `WebClient` 把 BOM POST 到 `PriceController`，断言响应中恰好只包含被请求的字段。

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

客户端请求（声明需要的 `PriceModel` 字段），以 `text/plain` 提交：

```json
{
  "originalPrice": "",
  "discount": "",
  "finalPrice": ""
}
```

返回（数值由固定数据的 Repository 产生）：

```json
{
  "originalPrice": 199.00,
  "discount": 2.40,
  "finalPrice": 196.60,
  "priceText": "￥196.60"
}
```

只请求部分字段时，例如 `{ "discount": "" }`，返回中只有 `discount` 有值，其余字段为 `null`，因为它们从未被订阅。

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

3. 重排后的 BOM 会被拆分成各下游理解的子 BOM 并直接传递 —— 不再使用嵌套 `flatMap`。`subBom` 辅助方法：`goods` 返回其嵌套 BOM；`discount` 叶子标记返回带该键的单键 BOM；键缺失时返回空 `Mono`（从而完全跳过该上游）：

   ```java
   Mono<Bom> goodsBom = subBom(upstreamBom, "goods");
   Mono<Bom> discountBom = subBom(upstreamBom, "discount");

   Mono<Goods> goods = goodsRepository.findById(goodsBom, goodsId).cache();
   Mono<BigDecimal> discount = discountOrchestrator.calculateDiscount(discountBom, goodsId).cache();
   Mono<BigDecimal> finalPrice = goods.zipWith(discount, this::calculateFinalPrice);

   Map<String, Publisher<?>> models = new HashMap<>();
   models.put("goods", goods);
   models.put("discount", discount);
   models.put("finalPrice", finalPrice);
   return (Mono<PriceModel>) (Publisher<?>) jsonBomMapper.map(bom, PriceModel.class, models);
   ```

   把关依然是自动的：映射器只订阅被请求的键，各仓储只填充其子 BOM 中存在的字段。`goods` 与 `discount` 这两个 Publisher 都做了缓存，因为它们各被两个字段消费（`originalPrice` 与 `finalPrice` 都需要商品；`discount` 与 `finalPrice` 都需要折扣）。如果没有缓存，`@PublisherLog` 的急切订阅与重复消费会各自构建出多余的 Publisher。

4. `GoodsRepository.findById` 与 `PromotionRepository.findByGoodsIdId` 只填充收到的子 BOM 中存在的字段，因此未被请求的字段不会被生产。

5. `DiscountOrchestrator.calculateDiscount(Mono<Bom> promotionBom, Mono<Long> goodsId)` 汇总该商品的促销折扣，只读取促销子 BOM 所请求的字段。

6. `PriceTextValueHandler` 把 `finalPrice` 转换为展示文案，值缺失时返回 `null`：

   ```java
   public String apply(Object model, String bomValue) {
       if (null == model) {
           return null;
       }
       return "￥" + model.toString();
   }
   ```

7. `getPriceModel` 与 `calculateDiscount` 上的 `@PublisherLog` 触发 `PublisherLogAdvice`：切面先缓存 `Mono`/`Flux` 类型的入参与返回值（日志不会引入额外订阅），再以 DEBUG 级别输出一行 JSON，包含 `class`、`method`、`args`、`result`。

## 运行应用

```
./gradlew :calculatePrice:bootRun
```

然后以 `text/plain` 提交一个 BOM：

```
curl -X POST http://localhost:18082/price/42 \
     -H "Content-Type: text/plain" \
     -d '{"originalPrice":"","discount":"","finalPrice":""}'
```

## 测试

| 测试 | 覆盖点 |
|------|--------|
| `PriceOrchestratorTest.GetPriceModelTest` | 全量/部分字段请求，走真实的 `ReactorJsonBomMapper` |
| `PriceOrchestratorTest.UpstreamBomTest` | 7 种字段组合下上游 BOM 的裁剪结果 |
| `PriceOrchestratorTest.CalculateFinalPriceTest` | 减法与最小为 0 的兜底 |
| `PriceOrchestratorTest.PriceTextValueHandlerTest` | `￥` 前缀、整数、null 处理 |
| `PublisherLogAdviceTest` | `Mono` / 普通对象 / `null` / 空 Publisher 入参与返回值的 AOP 日志 |
| `PriceEndpointIntegrationTest` | 真实 HTTP 跳转的端到端验证：`WebClient` 把 BOM POST 到 `/price/{goodsId}`，并断言只返回被请求的字段 |

运行：

```
./gradlew :calculatePrice:test
```

## 相关示例

- [示例编码规范](../CONVENTIONS.zh.md) — 所有示例共同遵循的编码约定
- [enumMap](../enumMap/README.zh.md) — 枚举驱动的模型注册
- [queryDbOnDemand](../queryDbOnDemand/README.zh.md) — 按需 SQL 列裁剪
- [mcpServer](../mcpServer/README.zh.md) — 面向 MCP 工具的 BOM 查询 Schema
