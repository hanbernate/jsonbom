# mcpServer — 把 BOM 查询暴露为 MCP 工具

[English](README.md) | **中文**

在 [calculatePrice](../calculatePrice/README.zh.md) 场景的基础上扩展。展示如何把 jsonbom 查询暴露为 [Model Context Protocol](https://modelcontextprotocol.io) 工具，让 LLM 客户端能够发现该工具并用 BOM 调用它。

展示的技术点：

1. 使用 Spring AI 的 `@Tool` / `@ToolParam` 注解声明 MCP 工具；
2. 自定义 `@BomType` 注解，把工具参数标记为 BOM 并指向其返回类型；
3. `BeanPostProcessor`（`JsonBomMCPBeanPostProcesser`）自动扫描 `@Tool` 方法并注册到任意 MCP server bean 上；
4. 使用 victools 生成工具的 JSON Schema，并通过自定义 `CustomPropertyDefinitionProvider` 把 `@BomType` 字段展开为返回对象的属性；
5. 通过一个小的包装抽象同时支持四种 MCP server 形态（同步/异步、有状态/无状态）；
6. 在 `application.yml` 中配置无状态的 streamable-HTTP MCP server。

## 场景

学生成绩服务暴露一个 MCP 工具 `queryGrade`，根据 `registryNum` 返回某个学生的成绩。该工具接收两个参数：

| 参数 | 类型 | 必填 | 含义 |
|------|------|------|------|
| `request` | object | 是 | 查询请求，包含 `registryNum` 与返回 `bom` |
| `userId` | integer | 否 | 调用者身份 |

`bom` 字段带有 `@BomType(Response.class)`，因此生成的工具 Schema 会把 BOM 描述为 `Response` 的形状（`lesson`、`score`）。这样 MCP 客户端（或 LLM）就能明确知道可以请求哪些字段。

## 项目结构

```
src/main/java/io/github/hanbernate/jsonbom/
├── core/
│   └── BomType.java                       # @BomType(Response.class) 字段注解
├── example/mcp/
│   ├── McpServerApplication.java          # Spring Boot 应用 + 注册 BeanPostProcessor
│   └── McpToolService.java                # @Tool queryGrade + Request/Response 模型
└── spring/
    └── JsonBomMCPBeanPostProcesser.java   # 扫描 @Tool 方法、构建 Schema、注册工具

src/main/resources/
└── application.yml                        # 无状态 streamable-HTTP MCP server 配置

src/test/java/io/github/hanbernate/jsonbom/example/
└── McpClientTest.java                     # 连接真实 MCP 客户端并断言工具 Schema
```

## 核心抽象

`@BomType` 把一个字段标记为 BOM，并记录用于描述它的返回类型：

```java
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface BomType {
    Class<?> value();
}
```

工具本身就是一个普通的 Spring bean：`@Tool` 暴露方法，`@ToolParam` 描述每个参数，`@BomType` 告诉 Schema 生成器 BOM 长什么样：

```java
@Service
public class McpToolService {

    @Tool(description = "Query grades of student by registryNum")
    public Response queryGrade(
            @ToolParam(description = "query request", required = true) Request request,
            @ToolParam(required = false) int userId) {
        return null;
    }

    @Data
    public static class Request {
        @ToolParam(description = "registryNum of the student")
        Long registryNum = 0L;

        @ToolParam(description = "response bom")
        @BomType(Response.class)
        Bom bom;
    }

    @Data
    public static class Response {
        @JsonPropertyDescription("lesson name")
        String lesson;

        @JsonPropertyDescription("the score of the lesson")
        int score;
    }
}
```

`JsonBomMCPBeanPostProcesser` 是核心引擎。它是一个 `BeanPostProcessor`，对每个 bean 判断它是否为 MCP server、是否声明了 `@Tool` 方法，然后为每个方法注册一个 `ToolCallback`：

```java
public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) {
    McpServerWrapper wrapper = createWrapperWhenMcpServer(bean);
    if (wrapper != null) {
        this.mcpServer = wrapper;
        wrapper.addToolCallbacks(this.callbacks);
    }
    // 扫描该 bean 的 @Tool 方法，为每个方法构建 ToolCallback
    ...
}
```

## 请求与返回

生成的工具输入 Schema（由测试断言）如下：

```json
{
  "type": "object",
  "required": ["request"],
  "properties": {
    "request": {
      "type": "object",
      "description": "query request",
      "required": ["bom"],
      "properties": {
        "registryNum": {
          "type": "integer",
          "format": "int64",
          "description": "registryNum of the student"
        },
        "bom": {
          "type": "object",
          "description": "response bom",
          "properties": {
            "lesson": { "type": "string", "description": "lesson name" },
            "score":  { "type": "string", "description": "the score of the lesson" }
          }
        }
      }
    },
    "userId": { "type": "integer" }
  }
}
```

客户端调用时把 BOM 作为普通参数传入：

```json
{
  "request": {
    "registryNum": 1001,
    "bom": { "lesson": "", "score": "" }
  },
  "userId": 7
}
```

## 实现细节

- **Schema 生成器。** 构造器中构建 victools `SchemaGenerator`，使用 `SchemaVersion.DRAFT_2020_12` 与 `OptionPreset.PLAIN_JSON`，加入 `JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED)`、`Swagger2Module` 与 `SpringAiSchemaModule`，开启 `Option.EXTRA_OPEN_API_FORMAT_VALUES` 与 `Option.PLAIN_DEFINITION_KEYS`，并关闭 `Option.SCHEMA_VERSION_INDICATOR`。
- **`BomPropertyDefinitionProvider`。** 一个注册到字段上的 `CustomPropertyDefinitionProvider<FieldScope>`。当字段带有 `@BomType` 时，返回一个 `object` 节点，其 `properties` 为目标返回类的成员字段，并复制该字段解析后的描述。未带 `@BomType` 的字段返回 `null`，因此保持默认生成行为。
- **嵌套类型。** `createByField` 会递归进入非基本类型、且非 `Number`/`Character`/`Boolean`/`String` 的类型来构建嵌套 `object` Schema；其余一律生成 `type: "string"`。
- **工具定义。** `buildToolDefinition` 构建根 `object` 节点，从 `@ToolParam(required = true)` 收集 `required`，用 `generator.generateSchema(parameter.getParameterizedType())` 生成每个参数的 Schema，并返回一个 `DefaultToolDefinition`，其 name/description 来自 `ToolUtils`。
- **回调构建。** `buiToolCallback` 构建 `MethodToolCallback`，传入生成的 `ToolDefinition`、`ToolMetadata.from(method)`、目标对象以及来自 `ToolUtils` 的结果转换器。
- **方法过滤。** 只处理带有 `@Tool` 的用户声明方法；函数式返回类型（`Function`、`Supplier`、`Consumer`）会被跳过；代理 bean 会通过 `AopUtils` 解析到目标类。
- **Server 形态。** `createWrapperWhenMcpServer` 把 `McpSyncServer`、`McpAsyncServer`、`McpStatelessSyncServer` 与 `McpStatelessAsyncServer` 分别映射到对应包装器，包装器调用 `McpToolUtils.toSyncToolSpecification` / `toAsyncToolSpecification` / `toStatelessSyncToolSpecification` / `toStatelessAsyncToolSpecification`，随后调用 `server.addTool(...)`。
- **配置。** `application.yml` 设置 `protocol: STATELESS` 以及无状态端点路径，使 server 以 streamable HTTP 通信且不保存会话状态。

## 测试

`McpClientTest` 在随机端口启动应用，通过 `WebClientStreamableHttpTransport` 连接真实 MCP 客户端，并断言发现到的工具 Schema：

| 断言 | 覆盖点 |
|------|--------|
| `listTools` 非空 | 工具已由后置处理器注册 |
| 顶层 `required` = `["request"]` | `@ToolParam(required = true)` 生效 |
| `request` 为 `object` 且描述为 `query request` | 参数描述被正确传递 |
| `request.required` 包含 `bom` | 嵌套 `@ToolParam` 的必填标记生效 |
| `registryNum` 为 `integer` / `int64` | 类型与 OpenAPI 格式正确生成 |
| `bom` 为 `object` 且含 `lesson`、`score` | `@BomType` 展开为返回结构 |

运行：

```
./gradlew :mcpServer:test
```

## 相关示例

- [calculatePrice](../calculatePrice/README.zh.md) — 基础按需计算
- [enumMap](../enumMap/README.zh.md) — 枚举驱动的模型注册
- [queryDbOnDemand](../queryDbOnDemand/README.zh.md) — 按需 SQL 列裁剪
