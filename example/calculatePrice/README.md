# calculatePrice — On-Demand Price Calculation

**English** | [中文](README.zh.md)

The basic jsonbom example (the starting scenario). Technical points it demonstrates:

1. A client query expressed as a BOM drives upstream data fetching; fields that were not requested trigger no fetching at all.
2. Describing the mapping between the response model and BOM paths with `@BomMapping`.
3. Reorganizing a response-oriented request BOM into a model-oriented BOM with `transformBom`.
4. Merging a derived field's missing dependencies into the upstream BOM with `Bom.merge`.
5. Registering several models as cold `Publisher`s, where the mapper subscribes only to the models needed by the requested fields.
6. Implementing field-level custom logic with a `ValueHandler`.
7. Logging reactive arguments and results of orchestrator methods with the `@PublisherLog` AOP advice.

## Scenario

A price endpoint returns some or all of the following fields:

| Field | Source | Description |
|-------|--------|-------------|
| `originalPrice` | goods model | price before discount |
| `discount` | promotion model | sum of all promotions for the goods |
| `finalPrice` | derived | `max(originalPrice - discount, 0)` |
| `priceText` | derived | `finalPrice` formatted as `￥...` |

The client asks only for what it needs. Each repository checks the BOM keys it receives, so only the requested data is produced; nothing is fetched for fields the client did not request.

## Project Layout

```
src/main/java/io/github/hanbernate/jsonbom/
├── example/
│   ├── AppConfig.java                # Spring configuration (ComponentScan + AspectJ proxy)
│   ├── PriceOrchestrator.java        # entry point: request BOM -> PriceModel
│   ├── DiscountOrchestrator.java     # discount aggregation
│   └── repository/
│       ├── GoodsRepository.java      # simulated goods data source
│       └── PromotionRepository.java  # simulated promotion data source
└── core/
    ├── PublisherLog.java             # @PublisherLog annotation
    └── PublisherLogAdvice.java       # AOP advice logging args / results of reactive methods
```

## Response Model

`PriceModel` declares where each field comes from:

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

`priceText` and `finalPrice` read the same BOM node; the value handler formats it as text.

## Request and Response

Client request (`PriceModel` fields it wants):

```json
{
  "originalPrice": "",
  "discount": "",
  "finalPrice": ""
}
```

Response (values produced by the simulated repositories):

```json
{
  "originalPrice": 199.00,
  "discount": 2.40,
  "finalPrice": 196.60,
  "priceText": "￥196.60"
}
```

A partial request such as `{ "discount": "" }` returns only `discount`; the other fields stay `null` and no goods query is issued.

## How It Works

1. `PriceOrchestrator.getPriceModel(Mono<Bom> bom, Mono<Long> goodsId)` rewrites the response-oriented BOM into a model-oriented BOM:

   ```java
   Mono<Bom> upstreamBom = bom.map(this::upstreamBom).cache();
   ```

   `upstreamBom` calls `jsonBomMapper.getBomAdapter().transformBom(targetBom, PriceModel.class)`, which re-organizes the BOM according to the `@BomMapping` paths of `PriceModel`:

   | Requested paths | Upstream BOM |
   |-----------------|--------------|
   | `originalPrice` | `{"goods":{"originalPrice":""}}` |
   | `discount` | `{"discount":""}` |
   | `finalPrice` / `priceText` | `{"finalPrice":"", "discount":"", "goods":{"originalPrice":""}}` |
   | `originalPrice` + `discount` | `{"goods":{"originalPrice":""}, "discount":""}` |
   | `originalPrice` + `finalPrice` | `{"goods":{"originalPrice":""}, "discount":"", "finalPrice":""}` |
   | `discount` + `finalPrice` | same as above |
   | all three | same as above |

2. When `finalPrice` is requested, the derived value needs both `discount` and `goods/originalPrice`. The orchestrator merges these into the upstream BOM:

   ```java
   r.merge("discount", BomOrValue.EMPTY);

   Bom goodsBom = new Bom();
   goodsBom.merge("originalPrice", BomOrValue.EMPTY);
   r.merge("goods", new BomOrValue(null, goodsBom));
   ```

   `Bom.merge` keeps existing keys untouched and recursively merges nested BOMs, so a client request for `originalPrice` is never overwritten. The goods BOM only ever asks for `originalPrice`; the discount is always fetched through the promotion model instead of the goods table.

   The request BOM is also the carrier that travels upstream: `transformBom(targetBom, PriceModel.class)` rewrites the client request into model paths, derived fields (`finalPrice`) merge their missing dependencies (`discount`, `goods/originalPrice`) into that BOM with `Bom.merge`, and every data source receives only its own sub-BOM (`upstreamBom.map(b -> b.getBom("goods"))`), so downstream code fetches exactly the requested fields.

3. `upstreamBom` is cached because it is consumed by both the goods lookup and the final price calculation.

4. Publishers are registered under model names that match the upstream BOM keys:

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

   The publishers are cold: the mapper subscribes only to the models needed by the requested fields, so unrequested models are never executed.

5. `GoodsRepository.findById` and `PromotionRepository.findByGoodsIdId` simulate database access and honor the BOM they receive: each field is filled only when its key is present.

   ```java
   if (b.containsKey("originalPrice")) {
       goods.setOriginalPrice(new BigDecimal("199.00"));
   }
   ```

6. `DiscountOrchestrator.calculateDiscount` builds a minimal promotion BOM that only carries `discount` and sums all promotion discounts into one `BigDecimal`.

7. `PriceTextValueHandler` converts the `finalPrice` value into display text and returns `null` when the value is absent:

   ```java
   public String apply(Object model, String bomValue) {
       if (null == model) {
           return null;
       }
       return "￥" + model.toString();
   }
   ```

8. `@PublisherLog` on `getPriceModel` and `calculateDiscount` activates `PublisherLogAdvice`. The advice first caches the `Mono`/`Flux` arguments and return value (so logging adds no extra subscriptions), then writes one JSON line per call at DEBUG level with `class`, `method`, `args` and `result`:

   ```json
   {"class":"io.github.hanbernate.jsonbom.example.PriceOrchestrator","method":"getPriceModel","args":{"bom":{"finalPrice":""},"goodsId":42},"result":{"originalPrice":199.00,"discount":2.40,"finalPrice":196.60,"priceText":"￥196.60"}}
   ```

## Tests

| Test | Coverage |
|------|----------|
| `PriceOrchestratorTest.GetPriceModelTest` | full and partial requests through the real `ReactorJsonBomMapper` |
| `PriceOrchestratorTest.UpstreamBomTest` | all 7 combinations of requested fields and the resulting upstream BOM |
| `PriceOrchestratorTest.CalculateFinalPriceTest` | subtraction and clamping to zero |
| `PriceOrchestratorTest.PriceTextValueHandlerTest` | `￥` prefix, integer values, null handling |
| `PublisherLogAdviceTest` | AOP logging for `Mono` / plain / `null` / empty arguments and results |

Run:

```
./gradlew :calculatePrice:test
```

## Related Examples

- [enumMap](../enumMap/README.md) — enum-based model registry
- [queryDbOnDemand](../queryDbOnDemand/README.md) — on-demand SQL projection
- [mcpServer](../mcpServer/README.md) — MCP tool schema for BOM queries
