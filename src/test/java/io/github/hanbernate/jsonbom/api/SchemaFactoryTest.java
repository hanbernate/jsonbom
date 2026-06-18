package io.github.hanbernate.jsonbom.api;


import io.github.hanbernate.jsonbom.spring.SpringBeanUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SchemaFactory test")
public class SchemaFactoryTest {
    ValueHandlers valueHandlers;
    SchemaFactory schemaFactory;

    public SchemaFactoryTest(){
        BeanUtil beanUtil = new SpringBeanUtil();

        DefaultValueHandlersImpl valueHandlers = new DefaultValueHandlersImpl();
        valueHandlers.setBeanUtil(beanUtil);
        this.valueHandlers = valueHandlers;

        DefaultSchemaFactoryImpl schemaFactory = new DefaultSchemaFactoryImpl();
        schemaFactory.setBeanUtil(beanUtil);
        schemaFactory.setValueHandlers(this.valueHandlers);
        this.schemaFactory = schemaFactory;
    }


    @Test
    public void empty(){
        class Empty{ }
        Schema<Empty> rs = schemaFactory.getByType(Empty.class);
        assertEquals(Empty.class, rs.getResponseType());
        assertTrue(CollectionUtils.isEmpty(rs.getChildren()));
    }

    @Test
    public void noWriter(){
        class TestType{
            @BomMapping("boxedModel")
            Integer boxed;
        }

        Schema<TestType> rs = schemaFactory.getByType(TestType.class);
        assertEquals(0, rs.getChildren().size());
    }

    @Test
    public void nest(){
        class SubType{
            @BomMapping("subPath")
            Integer field;

            public void setField(Integer field) {
                this.field = field;
            }
        }
        class TestType{
            @BomMapping("model")
            SubType sub;

            public void setSub(SubType sub) {
                this.sub = sub;
            }
        }

        Schema<TestType> rs = schemaFactory.getByType(TestType.class);
        assertEquals(1, rs.getChildren().size());

        Schema<?> child = rs.getChildren().get("sub");
        assertEquals(1, child.getPath().size());
        assertEquals("model", child.getPath().get(0));
        assertEquals("sub", child.getName());
        assertNotNull(child.getWriteMethod());
        assertEquals(SubType.class, child.getResponseType());

        Schema<?> grandChild = child.getChildren().get("field");
        assertEquals(1, grandChild.getPath().size());
        assertEquals("subPath", grandChild.getPath().get(0));
        assertEquals("field", grandChild.getName());
        assertNotNull(grandChild.getWriteMethod());
        assertEquals(Integer.class, grandChild.getResponseType());
    }

    @Test
    public void path(){
        class TestType{
            @BomMapping("path/basic")
            Integer basic;

            @BomMapping("/path/startWithSlash")
            Integer startWithSlash;

            @BomMapping("basic//doubleSlash")
            Integer doubleSlash;

            @BomMapping("")
            Integer empty;

            public void setBasic(Integer basic) {
                this.basic = basic;
            }

            public void setStartWithSlash(Integer startWithSlash) {
                this.startWithSlash = startWithSlash;
            }

            public void setDoubleSlash(Integer doubleSlash) {
                this.doubleSlash = doubleSlash;
            }

            public void setEmpty(Integer empty) {
                this.empty = empty;
            }
        }
        Schema<TestType> rs = schemaFactory.getByType(TestType.class);
        assertEquals(2, rs.getChildren().get("basic").getPath().size());
        assertEquals(2, rs.getChildren().get("startWithSlash").getPath().size());
        assertEquals(2, rs.getChildren().get("doubleSlash").getPath().size());
        assertEquals(0, rs.getChildren().get("empty").getPath().size());
    }

    @Test
    public void genericType() throws Exception{
        class TestSubType{
            @BomMapping("int")
            Integer integer;

            public void setInteger(Integer integer) {
                this.integer = integer;
            }
        }
        class TestType{
            @BomMapping(value = "model", genericType = TestSubType.class)
            List<TestSubType> list;

            public void setList(List<TestSubType> list) {
                this.list = list;
            }
        }
        Schema<TestType> rs = schemaFactory.getByType(TestType.class);
        Schema<?> list = rs.getChildren().get("list");
        assertEquals(List.class, list.getResponseType());
        assertEquals(TestSubType.class, list.getActualType());

        assertEquals(1, list.getChildren().size());
        assertTrue(list.getChildren().containsKey("integer"));

        class NoAnnotation{
            List<TestSubType> list;

            public void setList(List<TestSubType> list) {
                this.list = list;
            }

        }
        
        Schema<NoAnnotation> rs2 = schemaFactory.getByType(NoAnnotation.class);
        list = rs2.getChildren().get("list");
        assertEquals(List.class, list.getResponseType());
        assertEquals(TestSubType.class, list.getActualType());

        assertEquals(1, list.getChildren().size());
        assertTrue(list.getChildren().containsKey("integer"));
    }

    @Test
    public void nestInheritanceGeneric() throws Exception{
        class TestSubType{
            @BomMapping("int")
            Integer integer;

            public void setInteger(Integer integer) {
                this.integer = integer;
            }
        }


        abstract class GrandParent<T>{
            List<T> list;

            public void setList(List<T> list){
                this.list = list;
            }
        }

        class Parent<T> extends GrandParent<T>{

        }

        class Child extends Parent<TestSubType>{

        }
        
        Schema<Child> rs = schemaFactory.getByType(Child.class);
        Schema<?> list = rs.getChildren().get("list");
        assertEquals(List.class, list.getResponseType());
        assertEquals(TestSubType.class, list.getActualType());

        assertEquals(1, list.getChildren().size());
        assertTrue(list.getChildren().containsKey("integer"));
    }
    public static class TestValueHandler implements ValueHandler<Integer>{
        @Override
        public Integer apply(Object model, String bomValue) {
            return 0;
        }
    }

    @Test
    public void valueHandlerCahce(){
        class TestType{
            @BomMapping(value = "model", valueHandler = TestValueHandler.class)
            Integer field;
            @BomMapping(value = "model", valueHandler = TestValueHandler.class)
            Integer field2;

            public void setField(Integer field) {
                this.field = field;
            }

            public void setField2(Integer field2) {
                this.field2 = field2;
            }
        }
        Schema<TestType> rs = schemaFactory.getByType(TestType.class);
        Schema<?> child = rs.getChildren().get("field");
        ValueHandler<?> valueHandler = child.getValueHandler();
        assertEquals(TestValueHandler.class,valueHandler.getClass());
        assertEquals(valueHandler, rs.getChildren().get("field2").getValueHandler());
    }

    public static class TestRegister{}

    @Test
    public void registeredType(){
        ValueHandler<TestRegister> valueHandler = (model, bomValue) -> new TestRegister();
        valueHandlers.register(TestRegister.class, valueHandler);
        class TestType{
            @BomMapping("model")
            TestRegister field;

            public void setField(TestRegister field) {
                this.field = field;
            }
        }
        Schema<TestType> rs = schemaFactory.getByType(TestType.class);
        Schema<?> child = rs.getChildren().get("field");
        assertEquals(valueHandler, child.getValueHandler());
    }


    @Test
    public void primitive(){
        class BasicType{
            @BomMapping("primitiveModel")
            int primitive;

            @BomMapping("boxedModel")
            Integer boxed;

            @BomMapping("typeModel")
            Type type;

            public void setPrimitive(int primitive) {
                this.primitive = primitive;
            }

            public void setBoxed(Integer boxed) {
                this.boxed = boxed;
            }

            public void setType(Type type) {
                this.type = type;
            }
        }
        Schema<BasicType> rs = schemaFactory.getByType(BasicType.class);
        assertEquals(3, rs.getChildren().size());
        rs.getChildren().values().stream().forEach(v ->{
            assertTrue(CollectionUtils.isEmpty(v.getChildren()));
        });
    }

    @Test
    public void recursion() {
        class RecursionType {
            @BomMapping("valueModel")
            int value;

            @BomMapping("childModel")
            RecursionType child;

            public void setValue(int value) {
                this.value = value;
            }

            public void setChild(RecursionType child) {
                this.child = child;
            }
        }
        Schema<RecursionType> rs = schemaFactory.getByType(RecursionType.class);
        Map<String, Schema<?>> children = rs.getChildren();
        assertEquals(2, children.size());
        Map<String, Schema<?>> grandChildren = children.get("child").getChildren();
        assertEquals(children, grandChildren);
    }


    @Test
    public void valueNode(){

        class ValueNode {

            private Integer intValue;

            public void setIntValue(Integer intValue){
                this.intValue = intValue;
            }
                   
        }
        class Type {
            @BomMapping(value="valueModel", valueNode = true)
            ValueNode value;

            public void setValue(ValueNode value) {
                this.value = value;
            }
        }
        Schema<Type> rs = schemaFactory.getByType(Type.class);
        Map<String, Schema<?>> children = rs.getChildren();
        assertEquals(1, children.size());
        Schema<?> childSchema = children.get("value");
        assertEquals(0, childSchema.getChildren().size());

    }
    
}
