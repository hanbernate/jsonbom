package io.github.hanbernate.jsonbom.spring;

import io.github.hanbernate.jsonbom.api.*;
import io.github.hanbernate.jsonbom.api.model.ChildType;
import io.github.hanbernate.jsonbom.api.model.RegisteredType;
import io.github.hanbernate.jsonbom.api.model.RootType;
import io.github.hanbernate.jsonbom.api.valuehadnler.RegisteredTypeValueHandler;
import io.github.hanbernate.jsonbom.jackson.JacksonDeserializer;
import io.github.hanbernate.jsonbom.jackson.JacksonNameParser;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReactorJsonBomMapper test")
public class ReactorJsonBomMapperTest {

    private JsonBomMapper bomMapper;

    ObjectMapper jsonMapper;

    public ReactorJsonBomMapperTest(){
        ReactorJsonBomMapper mapper = new ReactorJsonBomMapper();
        mapper.setNameParser(new JacksonNameParser());
        mapper.registerValueHandler(RegisteredType.class, new RegisteredTypeValueHandler());
        this.bomMapper = mapper;

        ObjectMapper objectMapper = new ObjectMapper();
        JsonDeserializer<Bom> deserializer = new JacksonDeserializer();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Bom.class, deserializer);
        objectMapper.registerModule(module);
        this.jsonMapper = objectMapper;
    }

    private static <T> T unwarp(Publisher<T> publisher){
        return ((Mono<T>) publisher).block();
    }

    class Model{
        private int primitive = 0;

        private Integer boxed = 0;

        Type type = Type.VALUE;

        String string = "";

        public Model(int primitive, Integer boxed, String string) {
            this.primitive = primitive;
            this.boxed = boxed;
            this.string = string;
        }

        public int getPrimitive() {
            return primitive;
        }

        public Integer getBoxed() {
            return boxed;
        }

        public Type getType() {
            return type;
        }

        public String getString() {
            return string;
        }
    }

    @Test
    public void nest() throws IOException {
        Monos monos = new Monos(Mono.just(new Model(2, 3, "abc")), Mono.just(1), null, null);

        Map<String, Publisher<?>> models = Map.of("model", monos.getModel(), "primitive", monos.getPrimitive());

        String json = "{\"primitive\":\"\",\"child\":{\"primitive\":\"\",\"boxed\":\"\",\"type\":\"\",\"string\":\"\"}}";
        Bom bom = jsonMapper.readValue(json, Bom.class);

        RootType result = unwarp(bomMapper.map(Mono.just(bom), RootType.class, models));
        assertNotNull(result);
        assertEquals(1, result.getPrimitive());
        assertNull(result.getBoxed());
        assertNotNull(result.getChild());
        assertEquals(2, result.getChild().getPrimitive());
        assertEquals(3, result.getChild().getBoxed());
        assertEquals(Type.VALUE, result.getChild().getType());
        assertEquals("abc", result.getChild().getString());
        assertNull(result.getChild().getList());
    }

    @Test
    public void wrongBom() throws IOException {
        Monos monos = new Monos(Mono.just(new Model(2, 3, "abc")), Mono.just(1), null, null);

        Map<String, Publisher<?>> models = Map.of("model", monos.getModel(), "primitive", monos.getPrimitive());

        String json = "{\"primitive\":\"\",\"chil\":{\"primitive\":\"\",\"boxed\":\"\",\"type\":\"\",\"string\":\"\"}}";
        Bom bom = jsonMapper.readValue(json, Bom.class);

        RootType result = unwarp(bomMapper.map(Mono.just(bom), RootType.class, models));
        assertNotNull(result);
        assertEquals(1, result.getPrimitive());
        assertNull(result.getBoxed());
        assertNull(result.getChild());
    }

    @Test
    public void noModelAttributeExists() throws IOException {

        class Model{
            private int primitive = 1;

            public int getPrimitive() {
                return primitive;
            }
        }
        Map<String, Publisher<?>> models = Map.of("model", Mono.just(new Model()));

        String json = "{\"child\":{\"primitive\":\"\",\"boxed\":\"\",\"type\":\"\",\"string\":\"\"}}";
        Bom bom = jsonMapper.readValue(json, Bom.class);

        RootType result = unwarp(bomMapper.map(Mono.just(bom), RootType.class, models));
        assertNotNull(result);
        assertNotNull(result.getChild());
        assertEquals(1, result.getChild().getPrimitive());
        assertNull(result.getChild().getBoxed());
        assertNull(result.getChild().getType());
        assertNull(result.getChild().getString());
    }

    @Test
    public void missingModel() throws IOException {

        class Model{
            private int primitive = 1;

            public int getPrimitive() {
                return primitive;
            }
        }
        Map<String, Publisher<?>> models = Map.of("model", Mono.just(new Model()));

        String json = """
            {
                "child":{
                    "primitive":"",
                    "boxed":"",
                    "type":"",
                    "string":""
                },
                "boxed":""
            }
            """;
        Bom bom = jsonMapper.readValue(json, Bom.class);

        RootType result = unwarp(bomMapper.map(Mono.just(bom), RootType.class, models));
        assertNotNull(result);
        assertNull(result.getBoxed());
        assertNotNull(result.getChild());
        assertEquals(1, result.getChild().getPrimitive());
        assertNull(result.getChild().getBoxed());
        assertNull(result.getChild().getType());
        assertNull(result.getChild().getString());
    }

    @Test
    public void flux() throws IOException {
        Flux<Model> flux = Flux.range(1, 2)
                .map( i -> new Model(i, i + 1, String.valueOf(i)));
        Map<String, Publisher<?>> models = Map.of("children", flux);

        String json = "{\"children\":{\"primitive\":\"\",\"boxed\":\"\",\"string\":\"\"}}";
        Bom bom = jsonMapper.readValue(json, Bom.class);

        RootType result = unwarp(bomMapper.map(Mono.just(bom), RootType.class, models));
        List<ChildType> children = result.getChildren();
        assertEquals(2, children.size());

        ChildType child0 = children.get(0);
        assertEquals(1, child0.getPrimitive());
        assertEquals(2, child0.getBoxed());
        assertNull(child0.getType());
        assertEquals("1", child0.getString());

        ChildType child1 = children.get(1);
        assertEquals(2, child1.getPrimitive());
        assertEquals(3, child1.getBoxed());
        assertNull(child1.getType());
        assertEquals("2", child1.getString());
    }

    @Test
    public void collectionModel() throws IOException {
        class CollectionModel{
            List<Model> collection = IntStream.range(1, 3)
                    .mapToObj( i -> new Model(i, i + 1, String.valueOf(i)))
                    .collect(Collectors.toList());

            public List<Model> getCollection() {
                return collection;
            }
        }
        Map<String, Publisher<?>> models = Map.of("model", Mono.just(new CollectionModel()));

        String json = "{\"child\":{\"list\":{\"primitive\":\"\",\"boxed\":\"\",\"string\":\"\"},\"set\":{\"primitive\":\"\",\"boxed\":\"\",\"string\":\"\"},\"array\":{\"primitive\":\"\",\"boxed\":\"\",\"string\":\"\"}}}";
        Bom bom = jsonMapper.readValue(json, Bom.class);

        RootType result = unwarp(bomMapper.map(Mono.just(bom), RootType.class, models));
        ChildType child = result.getChild();

        assertEquals(2, child.getList().size());
        assertEquals(1, child.getList().get(0).getPrimitive());
        assertEquals(2, child.getList().get(0).getBoxed());
        assertEquals("1", child.getList().get(0).getString());
        assertEquals(2, child.getList().get(1).getPrimitive());
        assertEquals(3, child.getList().get(1).getBoxed());
        assertEquals("2", child.getList().get(1).getString());

        assertEquals(2, child.getSet().size());

        assertEquals(2, child.getArray().length);
        assertEquals(1, child.getArray()[0].getPrimitive());
        assertEquals(2, child.getArray()[0].getBoxed());
        assertEquals("1", child.getArray()[0].getString());
        assertEquals(2, child.getArray()[1].getPrimitive());
        assertEquals(3, child.getArray()[1].getBoxed());
        assertEquals("2", child.getArray()[1].getString());
    }

    @Test
    public void arrayModel() throws IOException {
        class ArrayModel{
            Model[] collection = IntStream.range(1, 3)
                .mapToObj( i -> new Model(i, i + 1, String.valueOf(i)))
                .toArray(Model[]::new);

            public Model[] getCollection() {
                return collection;
            }
        }
        Map<String, Publisher<?>> models = Map.of("model", Mono.just(new ArrayModel()));

        String json = "{\"child\":{\"list\":{\"primitive\":\"\",\"boxed\":\"\",\"string\":\"\"},\"set\":{\"primitive\":\"\",\"boxed\":\"\",\"string\":\"\"},\"array\":{\"primitive\":\"\",\"boxed\":\"\",\"string\":\"\"}}}";
        Bom bom = jsonMapper.readValue(json, Bom.class);

        RootType result = unwarp(bomMapper.map(Mono.just(bom), RootType.class, models));
        ChildType child = result.getChild();

        assertEquals(2, child.getList().size());
        assertEquals(1, child.getList().get(0).getPrimitive());
        assertEquals(2, child.getList().get(0).getBoxed());
        assertEquals("1", child.getList().get(0).getString());
        assertEquals(2, child.getList().get(1).getPrimitive());
        assertEquals(3, child.getList().get(1).getBoxed());
        assertEquals("2", child.getList().get(1).getString());

        assertEquals(2, child.getSet().size());

        assertEquals(2, child.getArray().length);
        assertEquals(1, child.getArray()[0].getPrimitive());
        assertEquals(2, child.getArray()[0].getBoxed());
        assertEquals("1", child.getArray()[0].getString());
        assertEquals(2, child.getArray()[1].getPrimitive());
        assertEquals(3, child.getArray()[1].getBoxed());
        assertEquals("2", child.getArray()[1].getString());

    }

    @Test
    public void map() throws IOException {
        Map<String, Publisher<?>> models = Map.of("model", Mono.just(Map.of("primitive",2, "boxed", 3, "string", "abc")));

        String json = "{\"child\":{\"primitive\":\"\",\"boxed\":\"\",\"type\":\"\",\"string\":\"\"}}";
        Bom bom = jsonMapper.readValue(json, Bom.class);

        RootType result = unwarp(bomMapper.map(Mono.just(bom), RootType.class, models));
        assertNotNull(result);
        assertNotNull(result.getChild());
        assertEquals(2, result.getChild().getPrimitive());
        assertEquals(3, result.getChild().getBoxed());
        assertEquals("abc", result.getChild().getString());
        assertNull(result.getChild().getList());

    }

    @Test
    public void oneInList() throws IOException {
        class CollectionModel{
            List<Model> collection = IntStream.range(1, 3)
                    .mapToObj( i -> new Model(i, i + 1, String.valueOf(i)))
                    .collect(Collectors.toList());

            public List<Model> getCollection() {
                return collection;
            }
        }
        Map<String, Publisher<?>> models = Map.of("model", Mono.just(new CollectionModel()));

        String json = "{\"child\":{\"first\":{\"primitive\":\"\",\"boxed\":\"\",\"string\":\"\"}}}";
        Bom bom = jsonMapper.readValue(json, Bom.class);

        RootType result = unwarp(bomMapper.map(Mono.just(bom), RootType.class, models));
        ChildType first = result.getChild().getFirst();
        assertEquals(1, first.getPrimitive());
        assertEquals(2, first.getBoxed());
        assertEquals("1", first.getString());
    }

    @Test
    public void oneInArray() throws IOException {
        class ArrayModel{
            Model[] collection = IntStream.range(1, 3)
                    .mapToObj( i -> new Model(i, i + 1, String.valueOf(i)))
                    .toArray(Model[]::new);

            public Model[] getCollection() {
                return collection;
            }
        }
        Map<String, Publisher<?>> models = Map.of("model", Mono.just(new ArrayModel()));

        String json = "{\"child\":{\"first\":{\"primitive\":\"\",\"boxed\":\"\",\"string\":\"\"}}}";
        Bom bom = jsonMapper.readValue(json, Bom.class);

        RootType result = unwarp(bomMapper.map(Mono.just(bom), RootType.class, models));
        ChildType first = result.getChild().getFirst();
        assertEquals(1, first.getPrimitive());
        assertEquals(2, first.getBoxed());
        assertEquals("1", first.getString());
    }

    @Test
    public void lazyCalculate() throws IOException {
        class CountModel {
            private int primitive = 2;

            private Integer boxed = 3;

            String string = "abc";

            public CountModel(int primitive, Integer boxed, String string) {
                this.primitive = primitive;
                this.boxed = boxed;
                this.string = string;
            }

            public int getPrimitive() {
                return primitive;
            }

            public Integer getBoxed() {
                return boxed;
            }

            public CountModel increPrimitive(){
                primitive ++;
                return this;
            }
        }
        CountModel countModel = new CountModel(1,2,"abc");

        Map<String, Publisher<?>> models = Map.of("model", Mono.just(countModel).map(m -> m.increPrimitive()));

        String json = "{}";
        Bom bom = jsonMapper.readValue(json, Bom.class);
        RootType result = unwarp(bomMapper.map(Mono.just(bom), RootType.class, models));
        assertNull(result.getChild());
        assertEquals(1, countModel.getPrimitive());

        json = "{\"child\":{\"primitive\":\"\",\"boxed\":\"\",\"string\":\"\"}}";
        bom = jsonMapper.readValue(json, Bom.class);
        result = unwarp(bomMapper.map(Mono.just(bom), RootType.class, models));
        assertNotNull(result.getChild());
        assertEquals(2, countModel.getPrimitive());

    }

    @Test
    public void emptyPath() throws IOException {
        Map<String, Publisher<?>> models = Map.of("model", Mono.just(new Model(1, 2, "abc")));

        String json = "{\"child\":{\"emptyPath\":{\"primitive\":\"\",\"boxed\":\"\",\"string\":\"\"}}}";
        Bom bom = jsonMapper.readValue(json, Bom.class);

        RootType result = unwarp(bomMapper.map(Mono.just(bom), RootType.class, models));
        ChildType first = result.getChild().getEmptyPath();
        assertEquals(1, first.getPrimitive());
        assertEquals(2, first.getBoxed());
        assertEquals("abc", first.getString());

    }

    @Test
    public void valueHandlerAnnotation() throws IOException {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Publisher<?>> models = Map.of("datetime", Mono.just(now));

        String pattern = "yyyy-MM-dd HH:mm:ss SSSS";
        String json = "{\"datetime\":\"" + pattern + "\"}";
        Bom bom = jsonMapper.readValue(json, Bom.class);

        RootType result = unwarp(bomMapper.map(Mono.just(bom), RootType.class, models));
        assertEquals(now.format(DateTimeFormatter.ofPattern(pattern)), result.getDatetime());
    }


    @Test
    public void registeryValueHandler() throws IOException {
        Map<String, Publisher<?>> models = Map.of("model", Mono.just(new Model(1, 2, "abc")));

        String json = "{\"registered\":\"value\"}";
        Bom bom = jsonMapper.readValue(json, Bom.class);

        RootType result = unwarp(bomMapper.map(Mono.just(bom), RootType.class, models));
        RegisteredType registeredType = result.getRegistered();
        assertEquals(Model.class.getName(), registeredType.getModelClassName());
        assertEquals("value", registeredType.getBomValue());
    }

    @Test
    public void jacksonAnnotation() throws IOException {

        Map<String, Publisher<?>> models = Map.of("model", Mono.just(new Model(1, 2, "abc")));

        String json = "{\"alias\":{\"primitive\":\"\",\"boxed\":\"\",\"string\":\"\"}}";
        Bom bom = jsonMapper.readValue(json, Bom.class);

        RootType result = unwarp(bomMapper.map(Mono.just(bom), RootType.class, models));
        ChildType origin = result.getOrigin();
        assertEquals(1, origin.getPrimitive());
        assertEquals(2, origin.getBoxed());
        assertEquals("abc", origin.getString());
    }

    @Test
    public void transform() throws IOException{
        String json = "{\"modelPrimitive\":\"\",\"boxed\":\"\",\"children\":{\"primitive\":\"\",\"boxed\":\"\",\"string\":\"\"},\"aliasString\":\"\"}";
        Bom targetBom = jsonMapper.readValue(json, Bom.class);

        Flux<Model> flux = Flux.range(100, 2)
            .map( i -> new Model(i, i + 1, String.valueOf(i)));
        Monos monos = new Monos(Mono.just(new Model(1, 2, "abc")), null, Mono.just(3), flux);

        TargetType result = unwarp(bomMapper.map(Mono.just(targetBom), TargetType.class, RootType.class, monos));
        assertEquals(1, result.getModelPrimitive());
        assertEquals(3, result.getBoxed());
        assertEquals(2, result.getChildren().size());
        assertEquals("abc", result.getAliasString());

        Map<String, Publisher<?>> models = Map.of("model", monos.getModel(),
                "boxed", monos.getBoxed(),
                "children", flux);
        result = unwarp(bomMapper.map(Mono.just(targetBom), TargetType.class, RootType.class, models));
        assertEquals(1, result.getModelPrimitive());
        assertEquals(3, result.getBoxed());
        assertEquals(2, result.getChildren().size());
        assertEquals("abc", result.getAliasString());

        

    }

    public static class TargetType{
        @BomMapping("child/primitive")
        private int modelPrimitive;

        @BomMapping("boxed")
        private Integer boxed;

        @BomMapping(value = "children", genericType = ChildType.class)
        private List<ChildType> children;

        @BomMapping("alias/string")
        private String aliasString;

        public int getModelPrimitive() {
            return modelPrimitive;
        }

        public void setModelPrimitive(int modelPrimitive) {
            this.modelPrimitive = modelPrimitive;
        }

        public Integer getBoxed() {
            return boxed;
        }

        public void setBoxed(Integer boxed) {
            this.boxed = boxed;
        }

        public List<ChildType> getChildren() {
            return children;
        }

        public void setChildren(List<ChildType> children) {
            this.children = children;
        }

        public String getAliasString() {
            return aliasString;
        }

        public void setAliasString(String aliasString) {
            this.aliasString = aliasString;
        }
    }

    public static class Monos implements BomModel{
        private Mono<Model> model;

        private Mono<Integer> primitive;

        private Mono<Integer> boxed;

        private Flux<Model> children;

        public Monos(Mono<Model>model, Mono<Integer> primitive, Mono<Integer> boxed, Flux<Model> children){
            this.model = model;
            this.primitive = primitive;
            this.boxed = boxed;
            this.children = children;
        }

        public Mono<Model> getModel() {
            return model;
        }

        public Mono<Integer> getPrimitive(){
            return primitive;
        }

        public Mono<Integer> getBoxed() {
            return boxed;
        }

        public Flux<Model> getChildren() {
            return children;
        }

        @Override
        public Map<String, Publisher<?>> getModels() {
            Map<String, Publisher<?>> map = new HashMap<>();
            map.put("model", model);
            map.put("primitive", primitive);
            map.put("boxed", boxed);
            map.put("children", children);
            return map;
        }
    }

}
