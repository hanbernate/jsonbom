# JsonBom

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

### 通过Maven或者Gradle引入依赖
Maven：
```
<dependency>
    <groupId>io.github.hanbernate</groupId>
    <artifactId>jsonbom</artifactId>
    <version>0.0.1</version>
</dependency>
```
Gradle：
```
implementation group: 'io.github.hanbernate', name: 'jsonbom', version: 0.0.1
```

### 集成Jackson反序列化JSON

首先注册BOM反序列化解析：

```
ObjectMapper objectMapper = new ObjectMapper();
JsonDeserializer<Bom> deserializer = new JacksonDeserializer();
SimpleModule module = new SimpleModule();
module.addDeserializer(Bom.class, deserializer);
objectMapper.registerModule(module);
```

在查询的pojo中增加BOM字段，如：
```
@Data
class Request{
    int examRegistrationNumber;
    Bom bom;
}
```

### 创建JsonBomMapper

```
JsonBomMapper jsonBomMapper = new ReactorBomMapper();
```

### 按需生成返回结果
在返回中增加BomMapping注解，标明映射关系：
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
调用JsonBomMapper，按需返回结果
```
Map<String, Publisher<?>> models = new HashMap<>();
models.put("user", Mono.just(new User("zhangsan", 25, "male")));
models.put("grades", Flux.just(new Grade("Math", 95), new Grade("Chinese", 60), new Grade("English", 80)));
Publisher<Response> response = jsonBomMapper.map(Mono.just(request.getBom()), Response.class,  models);
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

### `@JsonProperty`注解兼容
```
    ReactorJsonBomMapper mapper = new ReactorJsonBomMapper();
    mapper.setNameParser(new JacksonNameParser());
```

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
    mapper.registryValueHandler(RegisteredType.class, new RegisteredTypeValueHandler());
```

### Bom转换
```
Bom targetBom = bomAdapter.transformBom(sourceBom, TargetType.class);
```

### 异构模型转换
```
Mono<TargetType> target = jsonBomMapper.map(Mono.just(targetBom), TargetType.class, SourceType.class, models));
```

# 许可证

BSD 3-Clause License

# 联系方式
- 作者：Hanbernate
- 联系方式：ghost_lmh@163.com