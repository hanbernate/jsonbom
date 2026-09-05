# JsonBom

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

### Add Dependency via Maven or Gradle
Maven：
```
<dependency>
    <groupId>io.github.hanbernate</groupId>
    <artifactId>jsonbom</artifactId>
    <version>0.2.0.rc2</version>
</dependency>
```
Gradle：
```
implementation group: 'io.github.hanbernate', name: 'jsonbom', version: 0.2.0.rc2
```

### Integrate Jackson for JSON Deserialization

First, register the BOM deserializer:

```
ObjectMapper objectMapper = new ObjectMapper();
JsonDeserializer<Bom> deserializer = new JacksonDeserializer();
SimpleModule module = new SimpleModule();
module.addDeserializer(Bom.class, deserializer);
objectMapper.registerModule(module);
```

Add a BOM field to your query POJO:

```
@Data
class Request{
    int examRegistrationNumber;
    Bom bom;
}
```

### Create JsonBomMapper

```
JsonBomMapper jsonBomMapper = new ReactorBomMapper();
```

### Generate On-Demand Results
Add @BomMapping annotations to your response class to define mappings:

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
```
Use JsonBomMapper to get on-demand results:
```
Map<String, Publisher<?>> models = new HashMap<>();
models.put("user", Mono.just(new User("zhangsan", 25, "male")));
models.put("grades", Flux.just(new Grade("Math", 95), new Grade("Chinese", 60), new Grade("English", 80)));
Publisher<Response> response = jsonBomMapper.map(Mono.just(request.getBom()), Response.class,  models);
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

### `@JsonProperty`Annotation Compatibility
```
    ReactorJsonBomMapper mapper = new ReactorJsonBomMapper();
    mapper.setNameParser(new JacksonNameParser());
```

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
@Data
class Response{
    @BomMapping(value = "datetime", valueHandler = DateTimeFormatValueHandler.class)
    private String datetimeStr;
}
```

### Register Default ValueHandler for Specific Return Types
```
    JsonBomMapper mapper = new ReactorJsonBomMapper();
    mapper.registryValueHandler(RegisteredType.class, new RegisteredTypeValueHandler());
```

### BOM Transformation
```
Bom targetBom = bomAdapter.transformBom(sourceBom, TargetType.class);
```

### Heterogeneous Model Transformation
```
Mono<TargetType> target = jsonBomMapper.map(Mono.just(targetBom), TargetType.class, SourceType.class, models));
```

# License

BSD 3-Clause License

# Contact
- 作者：Hanbernate
- 联系方式：ghost_lmh@163.com