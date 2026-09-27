# advancedMapping — 高级 BOM 映射

[English](README.md) | **中文**

`advancedMapping` 示例聚焦 jsonbom 的高级映射能力。业务背景为电表抄表：抄表报表接口返回设备信息、抄表读数、告警、电量合计以及原始报文。

示例展示的技术点：

1. `genericType` —— 解析 `List` / `Set` / 数组返回字段的元素类型。
2. `valueNode` —— 把模型当作不透明叶子值，不再解析其内部字段。
3. 按返回类型注册的 `ValueHandler`（而非在每个字段上引用）。
4. `Set` 与数组返回，以及 `Flux` / `Mono` 两种数据源的规则。
5. 异构模型转换：由多个独立数据源构建源模型（`MeterSnapshot`），再展开为目标模型（`MeterReport`）。

## 场景

| 字段 | 映射 | 技术点 |
|------|------|--------|
| `deviceId` | `device/deviceId` | 两个字段共享同一个嵌套路径根与同一个源模型 |
| `deviceModel` | `device/model` | 与 `deviceId` 共用源模型 |
| `readings` | `readings`（`List<Reading>`） | `Flux` 驱动的 `List` 返回，元素类型由 `genericType` 指定 |
| `readingSet` | `readingSet`（`Set<Reading>`） | 同一个 `Flux` 驱动的 `Set` 返回 |
| `alerts` | `alerts`（`Reading[]`） | `Mono<Reading[]>` 驱动的数组返回 |
| `kwh` | `kwh`（`Kwh`） | 按类型注册的 `ValueHandler` |
| `raw` | `raw`（`RawPayload`） | `valueNode` 叶子值 |

客户端只请求其所需的字段。每个仓储收到的都是与其自身字段匹配的子 BOM，因此不会产生未请求的数据。

## 项目结构

```
src/main/java/io/github/hanbernate/jsonbom/example/
├── AppConfig.java                     # JsonBomMapper Bean（名称解析器 + 注册式 value handler）
├── MeterReportOrchestrator.java       # 入口：请求 BOM -> MeterReport
├── handler/
│   └── KwhValueHandler.java           # Kwh 返回类型的默认 ValueHandler
├── model/
│   ├── MeterReport.java               # 目标 / 返回模型
│   ├── MeterSnapshot.java             # 异构转换的源模型
│   ├── DeviceSnapshot.java            # deviceId / deviceModel 的数据源
│   ├── Reading.java                   # readings / readingSet / alerts 的元素
│   ├── Kwh.java                       # 由注册式 handler 映射
│   ├── KwhSample.java                 # 从仓储取出的原始值
│   └── RawPayload.java                # valueNode 叶子值
└── repository/
    ├── DeviceSnapshotRepository.java  # Mono<DeviceSnapshot>，遵循子 BOM
    ├── ReadingRepository.java         # Flux<Reading> 与 Mono<Reading[]>
    ├── KwhRepository.java             # Mono<KwhSample>
    └── RawPayloadRepository.java      # Mono<RawPayload>
```

## 返回模型

`MeterReport` 声明了每个字段的来源：

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

- `deviceId` 与 `deviceModel` 共享 `device` 路径根，因此都从同一个 `DeviceSnapshot` 实例写入。
- 集合/数组字段声明了 `genericType = Reading.class`，即使字段是裸数组 `Reading[]` 或 `Set`，映射器也能确定元素类型。
- `kwh` 的注解上没有 handler —— `Kwh` 类型是在全局注册的（见下文）。
- `raw` 是 `valueNode`：仓储返回的对象会被原样使用。

## 请求与返回

客户端请求（声明其想要的 `MeterReport` 字段）：

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

返回（由模拟仓储产生的值）：

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

部分请求（如 `{ "deviceId": "", "kwh": "" }`）只返回这两个字段，其余保持 `null`。

## 实现细节

1. `MeterReportOrchestrator.getMeterReport(Mono<Bom> bom, Mono<String> deviceId)` 先把请求 BOM 按 `MeterReport` 的路径转换并缓存：

   ```java
   Mono<Bom> upstreamBom = bom.map(this::upstreamBom).cache();
   ```

   `upstreamBom` 调用 `jsonBomMapper.getBomAdapter().transformBom(targetBom, MeterReport.class)`，把每个请求路径重定根到其首段：

   | 请求字段 | 转换后的 BOM |
   |----------|--------------|
   | `deviceId`、`deviceModel` | `{"device":{"deviceId":"","model":""}}` |
   | `readings` | `{"readings":""}` |
   | `readingSet` | `{"readingSet":""}` |
   | `alerts` | `{"alerts":""}` |
   | `kwh` | `{"kwh":""}` |
   | `raw` | `{"raw":""}` |

2. 每个仓储只收到自己的子 BOM。`subBom` 帮助方法屏蔽了 `Bom.getBom(key)` 的两种边界情况 —— 当键持有普通值时会抛异常，当键不存在时返回 `null` —— 改为返回空 `Bom`（Reactor 中 `Mono` 发出 `null` 是非法的）：

   ```java
   Mono<DeviceSnapshot> device =
           deviceSnapshotRepository.findById(subBom(upstreamBom, "device"), deviceId);
   Flux<Reading> readings =
           readingRepository.findByDeviceId(subBom(upstreamBom, "readings"), deviceId);
   ```

   `DeviceSnapshotRepository` 遵循收到的子 BOM，仅当键存在时才填充对应属性：

   ```java
   boolean wantsModel = bom.containsKey("model");
   boolean wantsLocation = bom.containsKey("location");
   return new DeviceSnapshot(id, wantsModel ? "M-9" : null,
           wantsLocation ? "Block A / Row 12" : null);
   ```

3. 所有模型按各自响应的 `path[0]` 注册。映射器只会订阅被请求字段所需的 publisher：

   ```java
   Map<String, Publisher<?>> models = new HashMap<>();
   models.put("device", device);
   models.put("readings", readings);
   // 同一个响应式数据源可以同时供 List 与 Set 字段使用。
   models.put("readingSet", readings);
   models.put("alerts", alerts);
   models.put("kwh", kwh);
   models.put("raw", raw);

   return (Mono<MeterReport>) (Publisher<?>) jsonBomMapper.map(bom, MeterReport.class, models);
   ```

   注意传给 `map` 的是**原始**请求 BOM（以字段名为键），而不是转换后的 BOM：`map` 按字段名遍历 BOM，因此转换后的（按路径重定根的）BOM 不能在此使用。

### `genericType`

`readings`、`readingSet` 与 `alerts` 声明了 `genericType = Reading.class`，因此即使是裸数组 `Reading[]` 或 `Set`，元素类型也是已知的。省略 `genericType` 时，映射器回退到字段签名推断（`DefaultSchemaFactoryImpl`）：对参数化的 `List<Reading>` / `Set<Reading>` 有效，但数组无效。

### `Set` 与数组返回

`ReadingRepository` 展示了两种受支持的数据源形态：

- `findByDeviceId` 返回 `Flux<Reading>`。`Flux` 可以收集为 `List` 或 `Set`，但**不能**映射为数组 —— 那会抛出 `JsonBomException`。
- `findAlerts` 返回 `Mono<Reading[]>`，这是给数组返回供值的受支持方式。

一个 `Flux` 可以同时支撑 `readings`（`List`）与 `readingSet`（`Set`），映射器会为每个字段分别收集。`Set` 还会对相等的元素去重。

### 注册式 `ValueHandler`

`AppConfig` 为 `Kwh` 返回类型注册了默认 handler：

```java
mapper.registerValueHandler(Kwh.class, new KwhValueHandler());
```

任何声明类型为 `Kwh` 的字段（例如 `MeterReport.kwh`）随后都会被自动视为叶子值并交给 `KwhValueHandler` 处理，无需逐个字段写 `@BomMapping.valueHandler`。该 handler 会归一化原始采样值并标注单位：

```java
public Kwh apply(Object model, String bomValue) {
    if (model instanceof KwhSample sample && sample.getRawValue() != null) {
        return new Kwh(sample.getRawValue().setScale(2, RoundingMode.HALF_UP), "kWh");
    }
    if (model instanceof Kwh kwh) {
        return kwh; // 已经转换过
    }
    if (bomValue != null && !bomValue.isBlank()) {
        return new Kwh(new BigDecimal(bomValue).setScale(2, RoundingMode.HALF_UP), "kWh");
    }
    return null;
}
```

### `valueNode`

`raw` 用 `valueNode = true` 声明，映射器不会解析 `RawPayload`。值请求时仓储对象被原样透传；若是嵌套 BOM 请求，则会构造一个空对象。

### 异构模型转换

`getMeterReportViaSnapshot` 展示了异构映射路径。请求 BOM 仍以**目标**字段名（`MeterReport`）为键，与 `getMeterReport` 完全一致。映射器先按 `MeterReport` 的路径转换它，从各独立数据源构建 `MeterSnapshot`，再把该快照展开为最终的 `MeterReport`：

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

使其成立的约定：

- `MeterSnapshot` 的每个子节点**名称**（`device`、`readings`、`readingSet`、`alerts`、`kwh`、`raw`）必须与 `MeterReport` 对应字段的 `path[0]` 一致。
- 每个子节点的 `path[0]` 选取 source-models map 中为其供值的条目。
- `MeterSnapshot.raw` 也必须声明为 `valueNode`，否则快照无法透传该对象。

## 测试

| 测试 | 覆盖内容 |
|------|----------|
| `MeterReportOrchestratorTest` | 通过编排器的全量与部分请求（简单流与快照流） |
| `GenericTypeTest` | `List` 元素解析、嵌套元素 BOM、数组元素解析、签名推断泛型 |
| `ValueNodeTest` | 值透传（同一实例）、嵌套 BOM 构造空对象、源缺失时字段为 null |
| `RegisteredValueHandlerTest` | 按类型把 handler 挂到字段 schema、原始采样转换、已转换值透传、单位回退 |
| `SetAndArrayResponseTest` | 一个 `Flux` 同时供 `List` 与 `Set`、`Set` 去重、`Mono<Reading[]>`、`Flux` 转数组抛 `JsonBomException` |
| `HeterogeneousTransformTest` | `transformBom` 重定根、嵌套字段名保留、快照展开、对请求字段的尊重 |

运行：

```
./gradlew :advancedMapping:test
```

## 相关示例

- [示例编码规范](../CONVENTIONS.zh.md) —— 所有示例共同遵循的编码约定
- [calculatePrice](../calculatePrice/README.zh.md) —— 基础的按需价格计算示例
- [enumMap](../enumMap/README.zh.md) —— 基于枚举的模型注册表
- [queryDbOnDemand](../queryDbOnDemand/README.zh.md) —— 按需 SQL 投影
- [mcpServer](../mcpServer/README.zh.md) —— 面向 BOM 查询的 MCP 工具 schema
