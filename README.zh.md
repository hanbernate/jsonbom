# JsonBom

[English](README.md) | **中文**

直接使用 JSON 的 API 查询语言。

## 介绍

主要用于客户端按需查询，核心功能包括：
1. 集成JSON反序列化组件，解析客户端查询需求；
2. 根据客户端需求，按需调用服务端代码；
3. 可字段级别自定义逻辑；
4. 异构查询语言与模型转换；


## 环境要求

- JDK 17+
- Reactor 3.0.0+

## 快速开始

以下步骤串起一次完整的按需查询：解析客户端查询为 `Bom`，用 `@BomMapping` 描述返回结构，再映射出被请求的数据。

示例使用 Lombok 的 `@Data` 并省略 import。

### 通过Maven或者Gradle引入依赖
Maven：
```
<dependency>
    <groupId>io.github.hanbernate</groupId>
    <artifactId>jsonbom</artifactId>
    <version>0.1.0</version>
</dependency>
```
Gradle：
```
implementation group: 'io.github.hanbernate', name: 'jsonbom', version: 0.1.0
```

### 集成Jackson反序列化JSON

注册BOM反序列化解析，使客户端查询可以被解析为 `Bom` 对象。Jackson 2 和 Jackson 3 均支持。

#### Jackson 2

```
ObjectMapper objectMapper = new ObjectMapper();
JsonDeserializer<Bom> deserializer = new JacksonDeserializer();
SimpleModule module = new SimpleModule();
module.addDeserializer(Bom.class, deserializer);
objectMapper.registerModule(module);
```

#### Jackson 3

```
JsonMapper jsonMapper = JsonMapper.builder()
    .addModule(new SimpleModule().addDeserializer(Bom.class, new Jackson3Deserializer()))
    .build();
```

### 在查询的pojo中增加BOM字段
```
@Data
class Request{
    int examRegistrationNumber;
    Bom bom;
}
```

### 创建JsonBomMapper

```
JsonBomMapper jsonBomMapper = new ReactorJsonBomMapper();
```

### 按需生成返回结果

在返回类中增加 `@BomMapping` 注解，标明字段的数据来源：
```
@Data
public class Response{
    @BomMapping("user/name")
    String name;

    @BomMapping("grades")
    List<Grade> grades;

    @Data
    public static class Grade{
        @BomMapping("lesson")
        String lesson;

        @BomMapping("score")
        int score;
    }
}

@Data
class User{
    String name;
    int age;
    String gender;
}
```

提供数据模型并映射请求 BOM（`request` 为反序列化后的查询对象）：
```
Map<String, Publisher<?>> models = new HashMap<>();
models.put("user", Mono.just(new User("zhangsan", 25, "male")));
models.put("grades", Flux.just(new Response.Grade("Math", 95), new Response.Grade("Chinese", 60), new Response.Grade("English", 80)));
Publisher<Response> response = jsonBomMapper.map(Mono.just(request.getBom()), Response.class, models);
```

### 请求与返回
客户端请求：
```
{
    "examRegistrationNumber":1234567,
    "bom":{
        "name":"",
        "grades":{
            "lesson":"",
            "score":""
        }
    }
}
```
服务端会按照客户端bom的结构返回：
```
{
    "name":"zhangsan",
    "grades":[{
        "lesson":"Math",
        "score":95
    },{
        "lesson":"Chinese",
        "score":60
    },{
        "lesson":"English",
        "score":80
    }]
}
```
## 进阶技巧

### `@JsonProperty` 注解兼容

使用 `JacksonNameParser` 从 Jackson 的 `@JsonProperty` 读取字段名：
```
ReactorJsonBomMapper mapper = new ReactorJsonBomMapper();
mapper.setNameParser(new JacksonNameParser());
```

### `@BomMapping` 属性

| 属性 | 说明 |
|------|------|
| `value` | 字段对应的 BOM 路径，如 `user/name`。 |
| `genericType` | 集合字段无法推断元素类型时指定，如 `@BomMapping(value = "grades", genericType = Grade.class)`。 |
| `valueHandler` | 作用在该字段上的 `ValueHandler` 实现。 |
| `valueNode` | 把字段标记为叶子值，不再为它构建子 BOM。 |

### 通过ValueHandler自定义Bom处理规则
ValueHandler可以通过感知JSON的值来进行个性化处理，使用方式：

声明ValueHandler:
```
public class DateTimeFormatValueHandler implements ValueHandler<String> {
    @Override
    public String apply(Object model, String bomValue) {
        LocalDateTime datetime = (LocalDateTime) model;
        return datetime.format(DateTimeFormatter.ofPattern(bomValue));
    }
}
```

在注解中指定ValueHandler：
```
class Response{
    @BomMapping(value = "datetime", valueHandler = DateTimeFormatValueHandler.class)
    private String datetimeStr;
}
```

### 为指定类型的返回值指定默认ValueHandler
```
JsonBomMapper mapper = new ReactorJsonBomMapper();
mapper.registerValueHandler(RegisteredType.class, new RegisteredTypeValueHandler());
```

### Bom转换
将一个 BOM 转换为目标类型定义的结构：
```
Bom targetBom = jsonBomMapper.getBomAdapter().transformBom(sourceBom, TargetType.class);
```

### 异构模型转换
```
Map<String, Publisher<?>> models = new HashMap<>();
// 填充源数据
Publisher<TargetType> target = jsonBomMapper.map(Mono.just(targetBom), TargetType.class, SourceType.class, models);
```

### 通过BomModel传入数据模型

`map` 除了 `Map`，也接受 `BomModel` 实现：
```
class MyModels implements BomModel {
    @Override
    public Map<String, Publisher<?>> getModels() {
        Map<String, Publisher<?>> models = new HashMap<>();
        models.put("user", Mono.just(new User("zhangsan", 25, "male")));
        return models;
    }
}

Publisher<Response> response = jsonBomMapper.map(Mono.just(request.getBom()), Response.class, new MyModels());
```

# 许可证

BSD 3-Clause License

# 联系方式
- 作者：Hanbernate
- 联系方式：ghost_lmh@163.com
