# enumMap — Enum-based Model Registry

**English** | [中文](README.zh.md)

Builds on the [calculatePrice](../calculatePrice/README.md) scenario. It shows how to replace model name strings with an enum and how a single orchestration method feeds several models to `jsonBomMapper.map`.

## Scenario

An order detail endpoint returns two models in one response:

| Model | Content |
|-------|---------|
| `order` | id, detail, status, price, timestamps |
| `orderLog` | status-change history: `before`, `after`, `createdAt` |

Each model is loaded by its own repository, and each repository only fills the fields present in its own sub-BOM. The model names (`order`, `orderLog`) are declared once by an enum that implements `BomModelField`, so orchestration code uses type-safe constants instead of string literals.

## Project Layout

```
src/main/java/io/github/hanbernate/jsonbom/
├── api/
│   ├── BomModelField.java            # model field contract: name + sub-BOM extraction
│   └── BomEnumModel.java             # BomModel backed by a Map, filled per enum constant
└── example/
    ├── OrderModelFieldEnum.java      # ORDER("order"), ORDER_LOG("orderLog")
    ├── OrderOrchestrator.java        # fills both models from one request BOM
    ├── OrderRepository.java          # order data source
    └── OrderLogRepository.java       # order log data source

src/test/java/io/github/hanbernate/jsonbom/example/
├── OrderResponse.java                # response definition: @BomMapping("order/..."), @BomMapping("orderLog")
├── OrderLogResponse.java             # log entry: before, after, time -> createdAt
└── OrderOrchestratorTest.java        # end-to-end test
```

## Core Abstractions

`BomModelField` maps an enum constant to a model name and extracts the matching sub-BOM from the request BOM:

```java
public interface BomModelField {
    String toModelName();

    default Bom toModelBom(Bom sourceBom) {
        return sourceBom.getBom(toModelName());
    }
}
```

```java
public enum OrderModelFieldEnum implements BomModelField {
    ORDER("order", OrderRepository.Order.class),
    ORDER_LOG("orderLog", OrderLogRepository.OrderLog.class),
    ;
    ...
}
```

`BomEnumModel<T extends Enum<T> & BomModelField>` is a `BomModel` implementation that keeps a `Map<String, Publisher<?>>` and fills it by enum constant:

```java
public <R> Mono<R> fillModel(T modelField, Mono<Bom> modelBom, Function<Mono<Bom>, Mono<R>> func) {
    Mono<R> result = modelBom.map(bom -> modelField.toModelBom(bom))
        .flatMap(bom -> func.apply(Mono.just(bom))).cache();
    this.set(modelField, result);
    return result;
}
```

`fillModel` extracts only the sub-BOM belonging to `modelField` and passes it to the loader, so each repository sees exactly the keys requested for its own model.

## Orchestration

```java
public BomEnumModel<OrderModelFieldEnum> findById(Mono<Bom> bom, Mono<Long> orderId) {
    BomEnumModel<OrderModelFieldEnum> models = new BomEnumModel<>();
    models.fillModel(OrderModelFieldEnum.ORDER, bom, b -> orderRepository.findById(b, orderId));
    models.fillModel(OrderModelFieldEnum.ORDER_LOG, bom, b -> orderLogRepository.findByOrderId(b, orderId).collectList());
    return models;
}
```

The returned model registry is passed to the mapper directly, because `BomEnumModel` implements `BomModel`:

```java
Bom bom = bomMapper.getBomAdapter().transformBom(requestBom, OrderResponse.class);
BomEnumModel<OrderModelFieldEnum> models = orchestrator.findById(Mono.just(bom), Mono.just(42L));
Mono<OrderResponse> response = (Mono<OrderResponse>) bomMapper.map(Mono.just(requestBom), OrderResponse.class, models);
```

## Request and Response

Client request:

```json
{
  "orderId": "",
  "detail": "",
  "status": "",
  "price": "",
  "logs": {
    "before": "",
    "after": "",
    "time": ""
  }
}
```

`transformBom(requestBom, OrderResponse.class)` rewrites the request into model paths: `orderId` -> `order/id`, `logs` -> `orderLog`, and `time` -> `orderLog/createdAt` (see `OrderLogResponse`). The orchestrator receives that transformed BOM, so `ORDER` sees `{id, detail, status, price}` and `ORDER_LOG` sees `{before, after, createdAt}`.

Response:

```json
{
  "orderId": 42,
  "detail": "detail msg",
  "status": 3,
  "price": 123,
  "logs": [
    { "before": "...", "after": "...", "time": "..." }
  ]
}
```

`orderLog` always returns all three status transitions in this example; only the requested keys of each log entry are populated.

## Implementation Details

- Model names live in the enum and are used both as the sub-BOM key and as the `models` map key, so the request BOM, the loaders, and the response mapping stay consistent.
- `OrderResponse` uses explicit paths for order fields and `@BomMapping("orderLog")` for the log list; `OrderLogResponse.time` is mapped from `orderLog/createdAt` while `before`/`after` are matched by field name with no annotation.
- `fillModel` calls `.cache()` on the loader result, so a model can be consumed by both the log list and any other field without re-querying.
- Each repository checks `containsKey` before filling a property, for example `OrderLogRepository` only builds `before`/`after` snapshots when those keys are requested.
- The loaded models are lazy: `findByOrderId` subscribes only if the response actually needs `orderLog`.
- The enum constant also stores the concrete model class (`actualClass`), an extension point if you want to use it for schema generation or documentation.
- Nothing here depends on Spring at runtime in the test: repositories are plain classes wired through setters.

## Tests

`OrderOrchestratorTest` transforms the request BOM with the real `ReactorJsonBomMapper`, calls the orchestrator, and asserts that:

- two models (`order`, `orderLog`) are registered,
- order fields (`orderId`, `detail`, `status`, `price`) are mapped,
- three log entries are returned with populated `before`, `after` and `time`.

Run:

```
./gradlew :enumMap:test
```

## Related Examples

- [calculatePrice](../calculatePrice/README.md) — basic on-demand calculation
- [queryDbOnDemand](../queryDbOnDemand/README.md) — on-demand SQL projection
- [mcpServer](../mcpServer/README.md) — MCP tool schema for BOM queries
