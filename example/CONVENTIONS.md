# Examples — Coding Conventions

**English** | [中文](CONVENTIONS.zh.md)

The modules under this directory are the reference implementations that ship with
jsonbom. Beyond showing *what* each example does, they also demonstrate *how* to
write jsonbom code. This document captures the conventions the examples follow —
the ones the maintainers review against. When you add or change an example, keep
it consistent with these rules.

The canonical, most complete example is
[`calculatePrice`](calculatePrice/README.md); it exercises every rule below end to
end, so it is used as the running reference here.

## The examples

| Example | Focus |
|---------|-------|
| [calculatePrice](calculatePrice/README.md) | On-demand price calculation; `@BomMapping`, `transformBom`, `@PublisherLog`, WebFlux |
| [advancedMapping](advancedMapping/README.md) | Nested paths, collections, custom `ValueHandler`s, heterogeneous transforms |
| [enumMap](enumMap/README.md) | Enum-driven model registry (`BomEnumModel`) |
| [queryDbOnDemand](queryDbOnDemand/README.md) | On-demand SQL column projection |
| [mcpServer](mcpServer/README.md) | Exposing BOM queries as MCP tools |

## Conventions

### 1. Controllers are reactive (WebFlux)

Entry points return `Mono` / `Flux`; never block and never build a plain `Map`
response by hand. This keeps the whole on-demand chain lazy, so an upstream is
only subscribed when a requested field actually needs it.

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

### 2. Controllers delegate; orchestrators own the data

A controller must not fabricate or assemble domain data. Its job is limited to
the HTTP boundary: read the request, parse the BOM, and delegate to an
**orchestrator** (a `@Component`). Orchestrators own the business/data logic and
are reusable from non-HTTP callers.

```java
// Controller -> orchestrator, one line, no containsKey, no entity assembly
return priceOrchestrator.getPriceModel(Mono.just(bom), Mono.just(goodsId));
```

See [`PriceOrchestrator`](calculatePrice/src/main/java/io/github/hanbernate/jsonbom/example/PriceOrchestrator.java)
and [`DiscountOrchestrator`](calculatePrice/src/main/java/io/github/hanbernate/jsonbom/example/DiscountOrchestrator.java).

### 3. Map with `JsonBomMapper`; never hand-roll `bom.containsKey(...)`

This is the core rule. Do **not** decide field-by-field whether to populate a
value with `bom.containsKey("x")`. Instead register one `Publisher` per field in a
`models` map and hand the BOM to [`JsonBomMapper.map(...)`](../src/main/java/io/github/hanbernate/jsonbom/api/JsonBomMapper.java).
The mapper iterates only the keys present in the BOM, so:

* a field that was **not requested** is never subscribed → no upstream call;
* a field that **was requested** is written;
* gating is automatic and lives in one place.

```java
Map<String, Publisher<?>> models = new HashMap<>();
models.put("goods", goods);
models.put("discount", discount);
models.put("finalPrice", finalPrice);
return (Mono<PriceModel>) (Publisher<?>) jsonBomMapper.map(bom, PriceModel.class, models);
```

```java
// Anti-pattern — do NOT do this in jsonbom code:
if (bom.containsKey("originalPrice")) {
    goods.setOriginalPrice(ORIGINAL_PRICE);
}
```

### 4. The `models` map is keyed by the BOM path root

`ReactorJsonBomMapper` looks a model up with `responseSchema.getPath().get(0)`
(the first segment of the mapped path):

| Model field | Resolved path | `models` key |
|-------------|---------------|--------------|
| `@BomMapping("discount")` | `[discount]` | `"discount"` |
| no annotation (`originalPrice`) | `[originalPrice]` | `"originalPrice"` |
| `@BomMapping("goods/originalPrice")` | `[goods, originalPrice]` | `"goods"` |

For a nested path the model publisher supplies the **root** object and the mapper
navigates the remaining segments. See
[`PriceOrchestrator`](calculatePrice/src/main/java/io/github/hanbernate/jsonbom/example/PriceOrchestrator.java)
(`models.put("goods", goods)` for `@BomMapping("goods/originalPrice")`).

> A `Flux` model can only be collected into a `List`/`Set` response field — never
> into an array. See [`ReactorJsonBomMapper.visit`](../src/main/java/io/github/hanbernate/jsonbom/spring/ReactorJsonBomMapper.java)
> for the exact rules.

### 5. Split the upstream BOM and cache the shared Publishers

Hand each downstream the sub-BOM it understands instead of gating with a nested
`flatMap`. A small `subBom` helper yields the nested BOM for a nested key, a
one-key BOM for a leaf marker (so `containsKey(...)` still works), and an empty
`Mono` when the key is absent so the downstream is skipped altogether:

```java
Mono<Bom> goodsBom = subBom(upstreamBom, "goods");
Mono<Bom> discountBom = subBom(upstreamBom, "discount");

Mono<Goods> goods = goodsRepository.findById(goodsBom, goodsId).cache();
Mono<BigDecimal> discount = discountOrchestrator.calculateDiscount(discountBom, goodsId).cache();
```

The gate is still automatic — the mapper only subscribes a requested key and each
repository only fills the fields present in its sub-BOM — but the wiring stays
flat. Keep the `.cache()` when a publisher is consumed by more than one field
(e.g. the goods value feeds both `originalPrice` and the derived `finalPrice`);
failing to cache builds a redundant subscription.

### 6. Declare model ↔ BOM paths with `@BomMapping`

Every response field that comes from the BOM carries `@BomMapping`, using the
separator to express nesting. Derived fields add their missing dependencies via
[`Bom.merge(...)`](../src/main/java/io/github/hanbernate/jsonbom/api/Bom.java)
during `transformBom`.

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

### 7. The wire format is the compact BOM JSON (Jackson 3)

A BOM travels as compact JSON whose leaf values are empty strings. The library
provides a Jackson 3 `Bom` deserializer, wired into a `JsonMapper` bean in
[`AppConfig`](calculatePrice/src/main/java/io/github/hanbernate/jsonbom/example/AppConfig.java);
a controller that takes the BOM as a `String` body injects that mapper and calls
`jsonMapper.readValue(body, Bom.class)`. The HTTP POJO codecs remain Jackson 2 —
the two Jackson versions intentionally coexist.

```json
{ "goods": { "originalPrice": "" }, "discount": "" }
```

### 8. Log reactive methods with `@PublisherLog`

Annotate orchestrator methods that take/return reactive types with
`@PublisherLog`. The advice caches arguments and results first (so logging adds
no extra subscription) and then emits one JSON line per call at DEBUG level.

```java
@PublisherLog
public Mono<BigDecimal> calculateDiscount(Mono<Bom> promotionBom, Mono<Long> goodsId) { ... }
```

## Checklist for a new example

- [ ] Endpoints are WebFlux (`Mono`/`Flux`); no blocking, no hand-built `Map` responses.
- [ ] Controllers only parse the request and delegate to orchestrators.
- [ ] Field population goes through `JsonBomMapper.map(...)` — no `bom.containsKey(...)` gating.
- [ ] `models` keys match the BOM path roots of the target model.
- [ ] Shared publishers are gated against the upstream BOM and cached.
- [ ] Response fields carry `@BomMapping`; derived fields merge their dependencies.
- [ ] Reactor methods that matter are annotated with `@PublisherLog`.
- [ ] `README.md` + `README.zh.md` describe the module and the on-demand behaviour.
- [ ] A test asserts the on-demand behaviour (only the requested fields are returned), not just the happy path.
