# mcpServer — BOM Query as an MCP Tool

**English** | [中文](README.zh.md)

Builds on the [calculatePrice](../calculatePrice/README.md) scenario. It shows how to expose a jsonbom query as a [Model Context Protocol](https://modelcontextprotocol.io) tool, so an LLM client can discover the tool and call it with a BOM.

Technical points it demonstrates:

1. Declaring an MCP tool with Spring AI's `@Tool` / `@ToolParam` annotations;
2. A custom `@BomType` annotation that marks a tool parameter as a BOM and points at its response class;
3. A `BeanPostProcessor` (`JsonBomMCPBeanPostProcesser`) that scans `@Tool` methods and registers them on any MCP server bean automatically;
4. Generating the tool's JSON Schema with victools, including a custom `CustomPropertyDefinitionProvider` that expands a `@BomType` field into the response object's properties;
5. Supporting all four MCP server flavours (sync/async, stateful/stateless) through a small wrapper abstraction;
6. A stateless streamable-HTTP MCP server configured in `application.yml`.

## Scenario

A student-grade service exposes one MCP tool, `queryGrade`, that returns the grade of a student identified by `registryNum`. The tool takes two parameters:

| Parameter | Type | Required | Meaning |
|-----------|------|----------|---------|
| `request` | object | yes | the query request, containing `registryNum` and the response `bom` |
| `userId` | integer | no | caller identity |

The `bom` field is annotated with `@BomType(Response.class)`, so the generated tool schema describes the BOM as the shape of `Response` (`lesson`, `score`). An MCP client (or an LLM) can therefore see exactly which fields it may request.

## Project Layout

```
src/main/java/io/github/hanbernate/jsonbom/
├── core/
│   └── BomType.java                       # @BomType(Response.class) field annotation
├── example/mcp/
│   ├── McpServerApplication.java          # Spring Boot app + registers the BeanPostProcessor
│   └── McpToolService.java                # @Tool queryGrade + Request/Response models
└── spring/
    └── JsonBomMCPBeanPostProcesser.java   # scans @Tool methods, builds schemas, registers tools

src/main/resources/
└── application.yml                        # stateless streamable-HTTP MCP server config

src/test/java/io/github/hanbernate/jsonbom/example/
└── McpClientTest.java                     # connects a real MCP client and asserts the tool schema
```

## Core Abstractions

`@BomType` marks a field as a BOM and records the response class used to describe it:

```java
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface BomType {
    Class<?> value();
}
```

The tool itself is a plain Spring bean. `@Tool` exposes the method, `@ToolParam` describes each argument, and `@BomType` tells the schema generator what the BOM looks like:

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

`JsonBomMCPBeanPostProcesser` is the engine. It is a `BeanPostProcessor` that, for every bean, checks whether it is an MCP server and whether it declares `@Tool` methods, then registers a `ToolCallback` for each one:

```java
public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) {
    McpServerWrapper wrapper = createWrapperWhenMcpServer(bean);
    if (wrapper != null) {
        this.mcpServer = wrapper;
        wrapper.addToolCallbacks(this.callbacks);
    }
    // scan the bean's @Tool methods and build a ToolCallback for each
    ...
}
```

## Request and Response

The generated tool input schema (as asserted by the test) is:

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

A client call carries the BOM as a normal argument:

```json
{
  "request": {
    "registryNum": 1001,
    "bom": { "lesson": "", "score": "" }
  },
  "userId": 7
}
```

## Implementation Details

- **Schema generator.** The constructor builds a victools `SchemaGenerator` with `SchemaVersion.DRAFT_2020_12` and `OptionPreset.PLAIN_JSON`, adds `JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED)`, `Swagger2Module` and `SpringAiSchemaModule`, enables `Option.EXTRA_OPEN_API_FORMAT_VALUES` and `Option.PLAIN_DEFINITION_KEYS`, and disables `Option.SCHEMA_VERSION_INDICATOR`.
- **`BomPropertyDefinitionProvider`.** A `CustomPropertyDefinitionProvider<FieldScope>` registered for fields. When a field carries `@BomType`, it returns an `object` node whose `properties` are the member fields of the target response class, and copies the field's resolved description. Fields without `@BomType` return `null`, so the default generation applies.
- **Nested types.** `createByField` recurses into non-primitive, non-`Number`/`Character`/`Boolean`/`String` types to build nested `object` schemas; everything else becomes `type: "string"`.
- **Tool definition.** `buildToolDefinition` builds the root `object` node, collects `required` from `@ToolParam(required = true)`, generates each parameter schema with `generator.generateSchema(parameter.getParameterizedType())`, and returns a `DefaultToolDefinition` whose name/description come from `ToolUtils`.
- **Callback construction.** `buiToolCallback` builds a `MethodToolCallback` with the generated `ToolDefinition`, `ToolMetadata.from(method)`, the target object, and the result converter from `ToolUtils`.
- **Method filtering.** Only user-declared methods annotated with `@Tool` are considered; functional return types (`Function`, `Supplier`, `Consumer`) are skipped, and proxied beans are resolved to their target class via `AopUtils`.
- **Server flavours.** `createWrapperWhenMcpServer` maps `McpSyncServer`, `McpAsyncServer`, `McpStatelessSyncServer` and `McpStatelessAsyncServer` to wrappers that call `McpToolUtils.toSyncToolSpecification` / `toAsyncToolSpecification` / `toStatelessSyncToolSpecification` / `toStatelessAsyncToolSpecification` and then `server.addTool(...)`.
- **Configuration.** `application.yml` sets `protocol: STATELESS` and the stateless endpoint path, so the server speaks streamable HTTP without session state.

## Tests

`McpClientTest` starts the application on a random port, connects a real MCP client over `WebClientStreamableHttpTransport`, and asserts the discovered tool schema:

| Assertion | Coverage |
|-----------|----------|
| `listTools` is non-empty | the tool was registered by the post-processor |
| top-level `required` = `["request"]` | `@ToolParam(required = true)` is honoured |
| `request` is an `object` with description `query request` | parameter description is propagated |
| `request.required` contains `bom` | nested `@ToolParam` required flags are honoured |
| `registryNum` is `integer` / `int64` | type and OpenAPI format are generated |
| `bom` is an `object` with `lesson` and `score` | `@BomType` expands into the response shape |

Run:

```
./gradlew :mcpServer:test
```

## Related Examples

- [Example conventions](../CONVENTIONS.md) — the coding conventions every example follows
- [calculatePrice](../calculatePrice/README.md) — basic on-demand calculation
- [enumMap](../enumMap/README.md) — enum-based model registry
- [queryDbOnDemand](../queryDbOnDemand/README.md) — on-demand SQL projection
