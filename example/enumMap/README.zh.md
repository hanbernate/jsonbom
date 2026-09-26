# enumMap — 枚举驱动的模型注册

[English](README.md) | **中文**

在 [calculatePrice](../calculatePrice/README.zh.md) 场景的基础上扩展。展示的技术点：

1. 用枚举替代模型名字符串，让编排代码使用类型安全的常量；
2. 一个编排方法同时向 `jsonBomMapper.map` 提供多个模型；
3. `BomEnumModel` 以按枚举常量填充的 `Map` 实现 `BomModel`。

## 场景

订单详情接口在一次响应中返回两个模型：

| 模型 | 内容 |
|------|------|
| `order` | id、detail、status、price、时间戳 |
| `orderLog` | 状态变更历史：`before`、`after`、`createdAt` |

每个模型由各自的 repository 装载，且每个 repository 只填充自己子 BOM 中出现的字段。模型名（`order`、`orderLog`）由实现 `BomModelField` 的枚举统一定义，编排代码因此使用类型安全常量而非字符串字面量。

## 项目结构

```
src/main/java/io/github/hanbernate/jsonbom/
├── api/
│   ├── BomModelField.java            # 模型字段契约：模型名 + 子 BOM 提取
│   └── BomEnumModel.java             # 基于 Map 的 BomModel，按枚举常量填充
└── example/
    ├── OrderModelFieldEnum.java      # ORDER("order")、ORDER_LOG("orderLog")
    ├── OrderOrchestrator.java        # 从一个请求 BOM 填充两个模型
    ├── OrderRepository.java          # 订单数据源
    └── OrderLogRepository.java       # 订单日志数据源

src/test/java/io/github/hanbernate/jsonbom/example/
├── OrderResponse.java                # 响应定义：@BomMapping("order/...")、@BomMapping("orderLog")
├── OrderLogResponse.java             # 日志条目：before、after、time -> createdAt
└── OrderOrchestratorTest.java        # 端到端测试
```

## 核心抽象

`BomModelField` 把一个枚举常量映射为模型名，并从请求 BOM 中提取对应的子 BOM：

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

`BomEnumModel<T extends Enum<T> & BomModelField>` 是一个 `BomModel` 实现，内部维护 `Map<String, Publisher<?>>`，并按枚举常量填充：

```java
public <R> Mono<R> fillModel(T modelField, Mono<Bom> modelBom, Function<Mono<Bom>, Mono<R>> func) {
    Mono<R> result = modelBom.map(bom -> modelField.toModelBom(bom))
        .flatMap(bom -> func.apply(Mono.just(bom))).cache();
    this.set(modelField, result);
    return result;
}
```

`fillModel` 只提取属于 `modelField` 的子 BOM 并交给装载器，因此每个 repository 看到的恰好是请求为其模型指定的键。

## 编排

```java
public BomEnumModel<OrderModelFieldEnum> findById(Mono<Bom> bom, Mono<Long> orderId) {
    BomEnumModel<OrderModelFieldEnum> models = new BomEnumModel<>();
    models.fillModel(OrderModelFieldEnum.ORDER, bom, b -> orderRepository.findById(b, orderId));
    models.fillModel(OrderModelFieldEnum.ORDER_LOG, bom, b -> orderLogRepository.findByOrderId(b, orderId).collectList());
    return models;
}
```

返回的模型注册表可以直接传给映射器，因为 `BomEnumModel` 实现了 `BomModel`：

```java
Bom bom = bomMapper.getBomAdapter().transformBom(requestBom, OrderResponse.class);
BomEnumModel<OrderModelFieldEnum> models = orchestrator.findById(Mono.just(bom), Mono.just(42L));
Mono<OrderResponse> response = (Mono<OrderResponse>) bomMapper.map(Mono.just(requestBom), OrderResponse.class, models);
```

## 请求与返回

客户端请求：

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

`transformBom(requestBom, OrderResponse.class)` 把请求重排为模型路径：`orderId` -> `order/id`，`logs` -> `orderLog`，`time` -> `orderLog/createdAt`（见 `OrderLogResponse`）。编排层收到的是重排后的 BOM，因此 `ORDER` 看到 `{id, detail, status, price}`，`ORDER_LOG` 看到 `{before, after, createdAt}`。

返回：

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

本示例中 `orderLog` 始终返回全部三条状态变更；每条日志只填充被请求的键。

## 实现细节

- 模型名定义在枚举中，同时用作子 BOM 键与 `models` 映射的键，因此请求 BOM、装载器与响应映射三者保持一致。
- `OrderResponse` 对订单字段使用显式路径，对日志列表使用 `@BomMapping("orderLog")`；`OrderLogResponse.time` 映射自 `orderLog/createdAt`，而 `before`/`after` 无需注解，按字段名匹配。
- `fillModel` 对装载结果调用 `.cache()`，因此同一个模型既可以供日志列表使用、也可以供其他字段使用，而不会重复查询。
- 每个 repository 在填充属性前都会检查 `containsKey`，例如 `OrderLogRepository` 只在请求了 `before`/`after` 时才构造状态快照。
- 装载的模型是惰性的：只有响应确实需要 `orderLog` 时，`findByOrderId` 才会被订阅。
- 枚举常量同时保存了具体的模型类（`actualClass`），这是一个扩展点，可用于 Schema 生成或文档。
- 测试运行时不依赖 Spring：repository 是普通类，通过 setter 注入。

## 测试

`OrderOrchestratorTest` 用真实的 `ReactorJsonBomMapper` 转换请求 BOM，调用编排方法，并断言：

- 注册了两个模型（`order`、`orderLog`）；
- 订单字段（`orderId`、`detail`、`status`、`price`）被映射；
- 返回三条日志条目，且 `before`、`after`、`time` 均已填充。

运行：

```
./gradlew :enumMap:test
```

## 相关示例

- [calculatePrice](../calculatePrice/README.zh.md) — 基础按需计算
- [queryDbOnDemand](../queryDbOnDemand/README.zh.md) — 按需 SQL 列裁剪
- [mcpServer](../mcpServer/README.zh.md) — 面向 MCP 工具的 BOM 查询 Schema
