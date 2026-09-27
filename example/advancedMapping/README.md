# advancedMapping — Advanced BOM Mapping

**English** | [中文](README.zh.md)

The `advancedMapping` example focuses on the advanced mapping features of jsonbom. The business backdrop is meter reading (电表抄表): a meter report endpoint returns a device, its readings, its alerts, its energy total and a raw payload.

Technical points it demonstrates:

1. `genericType` — resolving the element type of `List` / `Set` / array response fields.
2. `valueNode` — treating a model as an opaque leaf instead of introspecting its fields.
3. A `ValueHandler` registered by return type (instead of referenced per-field).
4. `Set` and array responses, including the `Flux` vs `Mono` source rules.
5. Heterogeneous model transformation: building a source model (`MeterSnapshot`) from independent sources and expanding it into the target (`MeterReport`).

## Scenario

| Field | Mapping | Feature |
|-------|---------|---------|
| `deviceId` | `device/deviceId` | two fields share one nested path root and one source model |
| `deviceModel` | `device/model` | same source model as `deviceId` |
| `readings` | `readings` (`List<Reading>`) | `List` response fed by a `Flux`, element type via `genericType` |
| `readingSet` | `readingSet` (`Set<Reading>`) | `Set` response fed by the same `Flux` |
| `alerts` | `alerts` (`Reading[]`) | array response fed by a `Mono<Reading[]>` |
| `kwh` | `kwh` (`Kwh`) | `ValueHandler` registered by type |
| `raw` | `raw` (`RawPayload`) | `valueNode` leaf |

The client asks only for what it needs. Every repository receives the sub-BOM that matches its own fields, so unrequested data is never produced.

## Project Layout

```
src/main/java/io/github/hanbernate/jsonbom/example/
├── AppConfig.java                     # JsonBomMapper bean (name parser + registered value handler)
├── MeterReportOrchestrator.java       # entry point: request BOM -> MeterReport
├── handler/
│   └── KwhValueHandler.java           # default ValueHandler for the Kwh return type
├── model/
│   ├── MeterReport.java               # target / response model
│   ├── MeterSnapshot.java             # source model for heterogeneous transformation
│   ├── DeviceSnapshot.java            # source of deviceId / deviceModel
│   ├── Reading.java                   # element of readings / readingSet / alerts
│   ├── Kwh.java                       # mapped by the registered handler
│   ├── KwhSample.java                 # raw value pulled from the repository
│   └── RawPayload.java                # valueNode leaf
└── repository/
    ├── DeviceSnapshotRepository.java  # Mono<DeviceSnapshot>, honors the sub-BOM
    ├── ReadingRepository.java         # Flux<Reading> and Mono<Reading[]>
    ├── KwhRepository.java             # Mono<KwhSample>
    └── RawPayloadRepository.java      # Mono<RawPayload>
```

## Response Model

`MeterReport` declares where each field comes from:

```java
@Data
public class MeterReport {
    @BomMapping("device/deviceId")
    private String deviceId;

    @BomMapping("device/model")
    private String deviceModel;

    @BomMapping(value = "readings", genericType = Reading.class)
    private List<Reading> readings;

    @BomMapping(value = "readingSet", genericType = Reading.class)
    private Set<Reading> readingSet;

    @BomMapping(value = "alerts", genericType = Reading.class)
    private Reading[] alerts;

    @BomMapping("kwh")
    private Kwh kwh;

    @BomMapping(value = "raw", valueNode = true)
    private RawPayload raw;
}
```

- `deviceId` and `deviceModel` share the `device` path root, so both are written from one `DeviceSnapshot` instance.
- The collection/array fields declare `genericType = Reading.class`, which lets the mapper build `Reading` elements without inferring them from the field signature.
- `kwh` carries no handler on the annotation — the `Kwh` type is registered globally (see below).
- `raw` is a `valueNode`: the repository object is used as-is.

## Request and Response

Client request (the `MeterReport` fields it wants):

```json
{
  "deviceId": "",
  "deviceModel": "",
  "readings": "",
  "readingSet": "",
  "alerts": "",
  "kwh": "",
  "raw": ""
}
```

Response (values produced by the simulated repositories):

```json
{
  "deviceId": "D-1",
  "deviceModel": "M-9",
  "readings": [
    { "readAt": "2026-01-01T00:00", "value": 12.5 },
    { "readAt": "2026-01-01T01:00", "value": 12.9 },
    { "readAt": "2026-01-01T02:00", "value": 13.1 }
  ],
  "readingSet": [ "..." ],
  "alerts": [
    { "readAt": "2026-01-01T02:00", "value": 99.0 },
    { "readAt": "2026-01-01T03:00", "value": 98.4 }
  ],
  "kwh": { "value": 153.46, "unit": "kWh" },
  "raw": { "hex": "0xA1B2C3", "length": 3 }
}
```

A partial request such as `{ "deviceId": "", "kwh": "" }` returns only those two fields; the rest stay `null`.

## How It Works

1. `MeterReportOrchestrator.getMeterReport(Mono<Bom> bom, Mono<String> deviceId)` transforms the request BOM through the paths of `MeterReport` and caches it:

   ```java
   Mono<Bom> upstreamBom = bom.map(this::upstreamBom).cache();
   ```

   `upstreamBom` calls `jsonBomMapper.getBomAdapter().transformBom(targetBom, MeterReport.class)`, which re-roots each requested path to its first segment:

   | Requested fields | Transformed BOM |
   |------------------|-----------------|
   | `deviceId`, `deviceModel` | `{"device":{"deviceId":"","model":""}}` |
   | `readings` | `{"readings":""}` |
   | `readingSet` | `{"readingSet":""}` |
   | `alerts` | `{"alerts":""}` |
   | `kwh` | `{"kwh":""}` |
   | `raw` | `{"raw":""}` |

2. Each repository receives only its own sub-BOM. The `subBom` helper shields the repositories from two edge cases of `Bom.getBom(key)` — it throws when the key holds a plain value and returns `null` when the key is absent — and returns an empty `Bom` instead (a `Mono` emitting `null` is illegal in Reactor):

   ```java
   Mono<DeviceSnapshot> device =
           deviceSnapshotRepository.findById(subBom(upstreamBom, "device"), deviceId);
   Flux<Reading> readings =
           readingRepository.findByDeviceId(subBom(upstreamBom, "readings"), deviceId);
   ```

   `DeviceSnapshotRepository` honors the sub-BOM it receives, filling each attribute only when its key is present:

   ```java
   boolean wantsModel = bom.containsKey("model");
   boolean wantsLocation = bom.containsKey("location");
   return new DeviceSnapshot(id, wantsModel ? "M-9" : null,
           wantsLocation ? "Block A / Row 12" : null);
   ```

3. All models are registered under the `path[0]` they answer to. The mapper only subscribes to the publishers of the requested fields:

   ```java
   Map<String, Publisher<?>> models = new HashMap<>();
   models.put("device", device);
   models.put("readings", readings);
   // One reactive source can feed both the List and the Set field.
   models.put("readingSet", readings);
   models.put("alerts", alerts);
   models.put("kwh", kwh);
   models.put("raw", raw);

   return (Mono<MeterReport>) (Publisher<?>) jsonBomMapper.map(bom, MeterReport.class, models);
   ```

   Note that the **original** request BOM (keyed by field names) is passed to `map`, not the transformed one: `map` iterates the BOM by field name, so the transformed (path-rooted) BOM cannot be used there.

### `genericType`

`readings`, `readingSet` and `alerts` declare `genericType = Reading.class` so the element type is known even for a raw `Reading[]` or a `Set`. When `genericType` is omitted the mapper falls back to the field signature (`DefaultSchemaFactoryImpl`), which works for parameterized `List<Reading>` / `Set<Reading>` but not for an array.

### `Set` and array responses

`ReadingRepository` shows the two supported source shapes:

- `findByDeviceId` returns a `Flux<Reading>`. A `Flux` can be collected into a `List` or a `Set`, but **cannot** be mapped to an array — that raises `JsonBomException`.
- `findAlerts` returns a `Mono<Reading[]>`, the supported way to feed an array response.

One `Flux` can back both `readings` (a `List`) and `readingSet` (a `Set`); the mapper collects it separately for each field. A `Set` also de-duplicates equal elements.

### Registered `ValueHandler`

`AppConfig` registers a default handler for the `Kwh` return type:

```java
mapper.registerValueHandler(Kwh.class, new KwhValueHandler());
```

Any field whose declared type is `Kwh` — such as `MeterReport.kwh` — is then automatically treated as a leaf and routed through `KwhValueHandler`, with no per-field `@BomMapping.valueHandler` required. The handler normalizes the raw sample and labels the unit:

```java
public Kwh apply(Object model, String bomValue) {
    if (model instanceof KwhSample sample && sample.getRawValue() != null) {
        return new Kwh(sample.getRawValue().setScale(2, RoundingMode.HALF_UP), "kWh");
    }
    if (model instanceof Kwh kwh) {
        return kwh; // already resolved
    }
    if (bomValue != null && !bomValue.isBlank()) {
        return new Kwh(new BigDecimal(bomValue).setScale(2, RoundingMode.HALF_UP), "kWh");
    }
    return null;
}
```

### `valueNode`

`raw` is declared with `valueNode = true`, so the mapper does not introspect `RawPayload`. The repository object is passed through unchanged for a value request; a nested BOM request would instead instantiate an empty object.

### Heterogeneous model transformation

`getMeterReportViaSnapshot` shows the heterogeneous mapping path. The request BOM is still keyed by the **target** field names (`MeterReport`), exactly like `getMeterReport`. The mapper first transforms it through `MeterReport`'s paths to build a `MeterSnapshot` from the independent data sources, then expands that snapshot into the final `MeterReport`:

```java
Map<String, Publisher<?>> sourceModels = new HashMap<>();
sourceModels.put("device", deviceSnapshotRepository.findById(
        Mono.just(Bom.createWithEmptyValue("deviceId", "model")), deviceId));
Flux<Reading> readings = readingRepository.findByDeviceId(Mono.just(new Bom()), deviceId);
sourceModels.put("readings", readings);
sourceModels.put("readingSet", readings);
sourceModels.put("alerts", readingRepository.findAlerts(Mono.just(new Bom()), deviceId));
sourceModels.put("kwh", kwhRepository.findLatest(Mono.just(new Bom()), deviceId));
sourceModels.put("raw", rawPayloadRepository.load(Mono.just(new Bom()), deviceId));

return (Mono<MeterReport>) (Publisher<?>) jsonBomMapper.map(
        bom, MeterReport.class, MeterSnapshot.class, sourceModels);
```

The contract that makes this work:

- Each child `name` of `MeterSnapshot` (`device`, `readings`, `readingSet`, `alerts`, `kwh`, `raw`) must match the `path[0]` of the corresponding `MeterReport` field.
- Each child's `path[0]` selects the entry of the source-models map that supplies its value.
- `MeterSnapshot.raw` must also be a `valueNode`, otherwise the snapshot would not carry the object through.

## Tests

| Test | Coverage |
|------|----------|
| `MeterReportOrchestratorTest` | full and partial requests through the orchestrator (both simple and snapshot flows) |
| `GenericTypeTest` | `List` element resolution, nested element BOMs, array element resolution, signature-inferred generics |
| `ValueNodeTest` | value passthrough (same instance), nested BOM builds an empty object, missing model leaves the field null |
| `RegisteredValueHandlerTest` | handler attached to the field schema by type, raw sample conversion, already-converted passthrough, unit fallbacks |
| `SetAndArrayResponseTest` | one `Flux` feeding both `List` and `Set`, `Set` de-duplication, `Mono<Reading[]>`, `Flux` to array raises `JsonBomException` |
| `HeterogeneousTransformTest` | `transformBom` re-rooting, nested field names preserved, snapshot expansion, requested-field respect |

Run:

```
./gradlew :advancedMapping:test
```

## Related Examples

- [Example conventions](../CONVENTIONS.md) — the coding conventions every example follows
- [calculatePrice](../calculatePrice/README.md) — the basic on-demand price calculation example
- [enumMap](../enumMap/README.md) — enum-based model registry
- [queryDbOnDemand](../queryDbOnDemand/README.md) — on-demand SQL projection
- [mcpServer](../mcpServer/README.md) — MCP tool schema for BOM queries
