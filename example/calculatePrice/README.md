# calculatePrice — On-Demand Price Calculation

**English** | [中文](README.zh.md)

The basic jsonbom example (the starting scenario). It runs as a Spring Boot WebFlux application so the whole on-demand flow can be exercised over HTTP. Technical points it demonstrates:

1. A client query expressed as a BOM drives upstream data fetching; fields that were not requested trigger no fetching at all.
2. Describing the mapping between the response model and BOM paths with `@BomMapping`.
3. Reorganizing a response-oriented request BOM into a model-oriented BOM with `transformBom`.
4. Merging a derived field's missing dependencies into the upstream BOM with `Bom.merge`.
5. Registering several models as cold `Publisher`s, where the mapper subscribes only to the models needed by the requested fields.
6. Implementing field-level custom logic with a `ValueHandler`.
7. Logging reactive arguments and results of orchestrator methods with the `@PublisherLog` AOP advice.
8. Reading the request BOM with **Jackson 3** (`tools.jackson` + `Jackson3Deserializer`).
9. Calling the endpoint over a real HTTP hop with **`WebClient`** — the integration test posts the request BOM to `PriceController` and asserts the on-demand response.

## Scenario

A price endpoint returns some or all of the following fields:

| Field | Source | Description |
|-------|--------|-------------|
| `originalPrice` | goods model | price before discount |
| `discount` | promotion model | sum of all promotions for the goods |
| `finalPrice` | derived | `max(originalPrice - discount, 0)` |
| `priceText` | derived | `finalPrice` formatted as `￥...` |

The client asks only for what it needs. The BOM is posted as the request body; the orchestrator passes the transformed sub-BOMs to the repositories, which fill only the requested fields.

## Project Layout

```
src/main/java/io/github/hanbernate/jsonbom/
├── example/
│   ├── PriceApplication.java         # @SpringBootApplication (scans the library + example)
│   ├── AppConfig.java                # AspectJ auto proxy + Jackson 3 JsonMapper / JsonBomMapper beans
│   ├── PriceOrchestrator.java        # entry point: request BOM -> PriceModel
│   ├── DiscountOrchestrator.java     # discount aggregation
│   ├── web/
│   │   └── PriceController.java      # POST /price/{goodsId} — accepts a JSON BOM
│   └── repository/
│       ├── GoodsRepository.java      # @Repository with fixed goods data
│       └── PromotionRepository.java  # @Repository with fixed promotion data
└── core/
    ├── PublisherLog.java             # @PublisherLog annotation
    └── PublisherLogAdvice.java       # AOP advice logging args / results of reactive methods
```

```
src/main/resources/
└── application.yml                   # server.port=18082
```

## HTTP Entry Point

`PriceController` accepts a compact JSON BOM as `text/plain` and responds with `application/json`:

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

The BOM is parsed with the injected Jackson 3 `JsonMapper` bean; the HTTP request/response bodies themselves are still (de)serialized by Spring WebFlux using Jackson 2, so the two Jackson versions coexist:

* **Jackson 3** owns `Bom` — the compact BOM JSON on the wire, read with the `JsonMapper` bean from `AppConfig`.
* **Jackson 2** owns the POJO codecs used for the `String` BOM body and the JSON responses.

## Data Sources

`GoodsRepository.findById` and `PromotionRepository.findByGoodsIdId` are in-process `@Repository` beans that hold fixed data so the example stays self-contained. Each one fills only the fields present in the sub-BOM it receives, mimicking a selective column query:

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

The real HTTP hop lives in the test: `PriceEndpointIntegrationTest` starts the application on its fixed port and a `WebClient` posts the BOM to `PriceController`, asserting that the response carries exactly the requested fields.

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

Client request (the `PriceModel` fields it wants), posted as `text/plain`:

```json
{
  "originalPrice": "",
  "discount": "",
  "finalPrice": ""
}
```

Response (values produced by the fixed-data repositories):

```json
{
  "originalPrice": 199.00,
  "discount": 2.40,
  "finalPrice": 196.60,
  "priceText": "￥196.60"
}
```

A partial request such as `{ "discount": "" }` returns only `discount`; the other fields stay `null` because they were never subscribed.

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

3. The transformed BOM is split into the sub-BOM each upstream understands and handed over directly — no nested `flatMap`. The `subBom` helper returns the nested BOM for `goods`, a one-key BOM for the `discount` leaf marker, and an empty `Mono` when a key is absent (so that upstream is skipped entirely):

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

   Gating stays automatic: the mapper only subscribes a requested key and each repository only fills the fields present in its sub-BOM. The `goods` and `discount` publishers are cached because each is consumed by two fields (`originalPrice` and `finalPrice` need the goods; `discount` and `finalPrice` need the discount). Without the cache, `@PublisherLog`'s eager subscription and the double consumption would each build a redundant publisher.

4. `GoodsRepository.findById` and `PromotionRepository.findByGoodsIdId` fill only the fields present in the sub-BOM they receive, so a field that was not requested is never produced.

5. `DiscountOrchestrator.calculateDiscount(Mono<Bom> promotionBom, Mono<Long> goodsId)` sums the discounts of every promotion, reading only the fields the promotion sub-BOM requested.

6. `PriceTextValueHandler` converts the `finalPrice` value into display text and returns `null` when the value is absent:

   ```java
   public String apply(Object model, String bomValue) {
       if (null == model) {
           return null;
       }
       return "￥" + model.toString();
   }
   ```

7. `@PublisherLog` on `getPriceModel` and `calculateDiscount` activates `PublisherLogAdvice`. The advice first caches the `Mono`/`Flux` arguments and return value (so logging adds no extra subscriptions), then writes one JSON line per call at DEBUG level with `class`, `method`, `args` and `result`.

## Run the Application

```
./gradlew :calculatePrice:bootRun
```

Then post a BOM as `text/plain`:

```
curl -X POST http://localhost:18082/price/42 \
     -H "Content-Type: text/plain" \
     -d '{"originalPrice":"","discount":"","finalPrice":""}'
```

## Tests

| Test | Coverage |
|------|----------|
| `PriceOrchestratorTest.GetPriceModelTest` | full and partial requests through the real `ReactorJsonBomMapper` |
| `PriceOrchestratorTest.UpstreamBomTest` | all 7 combinations of requested fields and the resulting upstream BOM |
| `PriceOrchestratorTest.CalculateFinalPriceTest` | subtraction and clamping to zero |
| `PriceOrchestratorTest.PriceTextValueHandlerTest` | `￥` prefix, integer values, null handling |
| `PublisherLogAdviceTest` | AOP logging for `Mono` / plain / `null` / empty arguments and results |
| `PriceEndpointIntegrationTest` | full end-to-end over a real HTTP hop: a `WebClient` posts a BOM to `/price/{goodsId}` and asserts that only the requested fields are returned |

Run:

```
./gradlew :calculatePrice:test
```

## Related Examples

- [Example conventions](../CONVENTIONS.md) — the coding conventions every example follows
- [enumMap](../enumMap/README.md) — enum-based model registry
- [queryDbOnDemand](../queryDbOnDemand/README.md) — on-demand SQL projection
- [mcpServer](../mcpServer/README.md) — MCP tool schema for BOM queries
