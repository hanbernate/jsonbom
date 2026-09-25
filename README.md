# JsonBom

**English** | [中文](README.zh.md)

A query language for APIs using plain JSON.

## Introduction

Designed primarily for client-side on-demand queries. Core features include:
1. Integrate JSON deserialization components to parse client query requirements;
2. Invoke server-side code on-demand based on client requirements;
3. Support field-level custom logic;
4. Transform between heterogeneous query languages and models.


## Requirements

- JDK 17+
- Reactor 3.0.0+

## Quick Start

The steps below build a complete on-demand query flow: parse the client query into a `Bom`, describe the response with `@BomMapping`, and map only the requested data.

Snippets use Lombok's `@Data` and omit imports.

### Add Dependency via Maven or Gradle
Maven:
```
<dependency>
    <groupId>io.github.hanbernate</groupId>
    <artifactId>jsonbom</artifactId>
    <version>0.1.0</version>
</dependency>
```
Gradle:
```
implementation group: 'io.github.hanbernate', name: 'jsonbom', version: 0.1.0
```

### Integrate Jackson for JSON Deserialization

Register the BOM deserializer so that the client query can be parsed into a `Bom` object. Both Jackson 2 and Jackson 3 are supported.

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

### Add a BOM Field to Your Query POJO

```
@Data
class Request{
    int examRegistrationNumber;
    Bom bom;
}
```

### Create JsonBomMapper

```
JsonBomMapper jsonBomMapper = new ReactorJsonBomMapper();
```

### Generate On-Demand Results

Add `@BomMapping` annotations to your response class to declare which fields are populated and where their data comes from:

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

Provide the source data as models and map the request BOM (`request` is the deserialized query POJO):

```
Map<String, Publisher<?>> models = new HashMap<>();
models.put("user", Mono.just(new User("zhangsan", 25, "male")));
models.put("grades", Flux.just(new Response.Grade("Math", 95), new Response.Grade("Chinese", 60), new Response.Grade("English", 80)));
Publisher<Response> response = jsonBomMapper.map(Mono.just(request.getBom()), Response.class, models);
```

### Request and Response
Client request:
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
The server will return based on the client's BOM structure:
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
## Advanced Topics

### `@JsonProperty` Annotation Compatibility

Use `JacksonNameParser` to take field names from Jackson's `@JsonProperty`:
```
ReactorJsonBomMapper mapper = new ReactorJsonBomMapper();
mapper.setNameParser(new JacksonNameParser());
```

### `@BomMapping` Attributes

| Attribute | Description |
|-----------|-------------|
| `value` | BOM path of the field, for example `user/name`. |
| `genericType` | Element type for a collection field when it cannot be inferred, for example `@BomMapping(value = "grades", genericType = Grade.class)`. |
| `valueHandler` | A `ValueHandler` implementation applied to this field. |
| `valueNode` | Marks the field as a leaf value; no child BOM is built for it. |

### Custom BOM Processing Rules with ValueHandler
ValueHandler enables personalized processing by interpreting JSON values.

Declare a ValueHandler:
```
public class DateTimeFormatValueHandler implements ValueHandler<String> {
    @Override
    public String apply(Object model, String bomValue) {
        LocalDateTime datetime = (LocalDateTime) model;
        return datetime.format(DateTimeFormatter.ofPattern(bomValue));
    }
}
```

Specify the ValueHandler in your annotation:
```
class Response{
    @BomMapping(value = "datetime", valueHandler = DateTimeFormatValueHandler.class)
    private String datetimeStr;
}
```

### Register Default ValueHandler for Specific Return Types
```
JsonBomMapper mapper = new ReactorJsonBomMapper();
mapper.registerValueHandler(RegisteredType.class, new RegisteredTypeValueHandler());
```

### BOM Transformation
Transforms a BOM into the structure defined by a target type:
```
Bom targetBom = jsonBomMapper.getBomAdapter().transformBom(sourceBom, TargetType.class);
```

### Heterogeneous Model Transformation
```
Map<String, Publisher<?>> models = new HashMap<>();
// populate models with source data publishers
Publisher<TargetType> target = jsonBomMapper.map(Mono.just(targetBom), TargetType.class, SourceType.class, models);
```

### Pass Models with BomModel

`map` also accepts a `BomModel` implementation instead of a `Map`:
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

# License

BSD 3-Clause License

# Contact
- Author: Hanbernate
- Email: ghost_lmh@163.com
