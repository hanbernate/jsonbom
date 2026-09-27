# 示例 — 编码规范

[English](CONVENTIONS.md) | **中文**

本目录下的各个模块是 jsonbom 随库发布的参考实现。它们不仅演示每个示例“做什么”，
也演示“如何写”jsonbom 代码。本文档沉淀这些示例共同遵循的约定 —— 也就是代码评审时
依据的规则。新增或修改示例时，请与这些规则保持一致。

最完整、最具代表性的示例是
[`calculatePrice`](calculatePrice/README.zh.md)，它端到端覆盖了下面的全部规则，
因此本文以它作为示例引用。

## 示例列表

| 示例 | 侧重点 |
|------|--------|
| [calculatePrice](calculatePrice/README.zh.md) | 按需计算价格；`@BomMapping`、`transformBom`、`@PublisherLog`、WebFlux |
| [advancedMapping](advancedMapping/README.zh.md) | 嵌套路径、集合、自定义 `ValueHandler`、异构转换 |
| [enumMap](enumMap/README.zh.md) | 枚举驱动的模型注册（`BomEnumModel`） |
| [queryDbOnDemand](queryDbOnDemand/README.zh.md) | 按需 SQL 列裁剪 |
| [mcpServer](mcpServer/README.zh.md) | 把 BOM 查询暴露为 MCP 工具 |

## 约定

### 1. 控制器使用响应式（WebFlux）

入口返回 `Mono` / `Flux`；不要阻塞，也不要手写 `Map` 拼装响应。这样整条按需链路
保持惰性：只有被请求的字段真正需要时，才会订阅对应的上游。

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

### 2. 控制器只做转发；数据由编排器负责

控制器不得自行编造或组装领域数据。它的职责仅限于 HTTP 边界：读取请求、解析 BOM，
然后委托给 **编排器**（`@Component`）。编排器承载业务/数据逻辑，并可被非 HTTP 调用方复用。

```java
// 控制器 -> 编排器：一行，无 containsKey，无实体拼装
return priceOrchestrator.getPriceModel(Mono.just(bom), Mono.just(goodsId));
```

参见 [`PriceOrchestrator`](calculatePrice/src/main/java/io/github/hanbernate/jsonbom/example/PriceOrchestrator.java)
与 [`DiscountOrchestrator`](calculatePrice/src/main/java/io/github/hanbernate/jsonbom/example/DiscountOrchestrator.java)。

### 3. 用 `JsonBomMapper` 映射，绝不手写 `bom.containsKey(...)`

这是核心规则。**不要**用 `bom.containsKey("x")` 逐个判断字段是否填充。正确做法是：
为每个字段准备一个 `Publisher` 放进 `models`，把 BOM 交给
[`JsonBomMapper.map(...)`](../src/main/java/io/github/hanbernate/jsonbom/api/JsonBomMapper.java)。
映射器只遍历 BOM 中实际存在的键，因此：

* **未被请求**的字段不会被订阅 → 不会触发上游调用；
* **被请求**的字段会被写入；
* 按需把关是自动的，且集中在一处。

```java
Map<String, Publisher<?>> models = new HashMap<>();
models.put("goods", goods);
models.put("discount", discount);
models.put("finalPrice", finalPrice);
return (Mono<PriceModel>) (Publisher<?>) jsonBomMapper.map(bom, PriceModel.class, models);
```

```java
// 反模式 —— jsonbom 代码中不要这样写：
if (bom.containsKey("originalPrice")) {
    goods.setOriginalPrice(ORIGINAL_PRICE);
}
```

### 4. `models` 以 BOM 路径的根段为键

`ReactorJsonBomMapper` 用 `responseSchema.getPath().get(0)`（映射路径的第一个段）查找模型：

| 模型字段 | 解析出的路径 | `models` 的键 |
|----------|--------------|---------------|
| `@BomMapping("discount")` | `[discount]` | `"discount"` |
| 无注解（`originalPrice`） | `[originalPrice]` | `"originalPrice"` |
| `@BomMapping("goods/originalPrice")` | `[goods, originalPrice]` | `"goods"` |

对于嵌套路径，模型 Publisher 提供的是**根对象**，映射器再沿剩余路径导航。参见
[`PriceOrchestrator`](calculatePrice/src/main/java/io/github/hanbernate/jsonbom/example/PriceOrchestrator.java)
（`@BomMapping("goods/originalPrice")` 对应 `models.put("goods", goods)`）。

> `Flux` 模型只能收集到 `List`/`Set` 字段，**不能**收集到数组字段。确切规则见
> [`ReactorJsonBomMapper.visit`](../src/main/java/io/github/hanbernate/jsonbom/spring/ReactorJsonBomMapper.java)。

### 5. 拆分上游 BOM，并缓存共享的 Publisher

把各下游理解的子 BOM 直接交给它，而不是用嵌套 `flatMap` 来把关。一个小的 `subBom`
辅助方法：嵌套键返回其嵌套 BOM；叶子标记返回仍带该键的单键 BOM（因此
`containsKey(...)` 依然成立）；键缺失时返回空 `Mono`，从而完全跳过该下游：

```java
Mono<Bom> goodsBom = subBom(upstreamBom, "goods");
Mono<Bom> discountBom = subBom(upstreamBom, "discount");

Mono<Goods> goods = goodsRepository.findById(goodsBom, goodsId).cache();
Mono<BigDecimal> discount = discountOrchestrator.calculateDiscount(discountBom, goodsId).cache();
```

把关依然是自动的 —— 映射器只订阅被请求的键，各仓储只填充其子 BOM 中存在的字段 ——
但接线保持扁平。当一个 Publisher 被多个字段消费时（例如商品值同时供给
`originalPrice` 与派生字段 `finalPrice`）仍要 `.cache()`；未做缓存会构建出多余的订阅。

### 6. 用 `@BomMapping` 声明模型与 BOM 路径的对应

所有来自 BOM 的响应字段都要带 `@BomMapping`，用分隔符表达嵌套。派生字段在
`transformBom` 阶段通过
[`Bom.merge(...)`](../src/main/java/io/github/hanbernate/jsonbom/api/Bom.java)
补齐其缺失的依赖。

```java
@Data
public static class PriceModel {
    @BomMapping("goods/originalPrice") private BigDecimal originalPrice;
    @BomMapping("discount")            private BigDecimal discount;
    @BomMapping("finalPrice")          private BigDecimal finalPrice;
    @BomMapping(value = "finalPrice", valueHandler = PriceTextValueHandler.class)
    private String priceText;
}
```

### 7. 传输格式是紧凑 BOM JSON（Jackson 3）

BOM 以紧凑 JSON 传输，叶子值为空字符串。库提供 Jackson 3 的 `Bom` 反序列化器，在
[`AppConfig`](calculatePrice/src/main/java/io/github/hanbernate/jsonbom/example/AppConfig.java)
中注册到 `JsonMapper` Bean；把 BOM 作为 `String` 请求体接收的控制器注入该映射器并调用
`jsonMapper.readValue(body, Bom.class)` 即可。HTTP POJO 编解码仍由 Jackson 2 负责 ——
两个 Jackson 版本是有意共存的。

```json
{ "goods": { "originalPrice": "" }, "discount": "" }
```

### 8. 用 `@PublisherLog` 记录响应式方法

对入参/返回值为响应式类型的编排器方法加 `@PublisherLog`。切面先缓存入参与返回值
（因此日志不会引入额外订阅），再以 DEBUG 级别每次调用输出一行 JSON。

```java
@PublisherLog
public Mono<BigDecimal> calculateDiscount(Mono<Bom> promotionBom, Mono<Long> goodsId) { ... }
```

## 新示例自检清单

- [ ] 端点使用 WebFlux（`Mono`/`Flux`）；不阻塞，不手写 `Map` 响应。
- [ ] 控制器只解析请求并转发给编排器。
- [ ] 字段填充统一走 `JsonBomMapper.map(...)` —— 不用 `bom.containsKey(...)` 把关。
- [ ] `models` 的键与目标模型的 BOM 路径根段一致。
- [ ] 共享的 Publisher 按上游 BOM 把关并缓存。
- [ ] 响应字段带 `@BomMapping`；派生字段补齐依赖。
- [ ] 关键响应式方法带 `@PublisherLog`。
- [ ] 提供 `README.md` + `README.zh.md`，说明模块与按需行为。
- [ ] 有测试断言按需行为（只返回被请求的字段），而非仅覆盖正常路径。
