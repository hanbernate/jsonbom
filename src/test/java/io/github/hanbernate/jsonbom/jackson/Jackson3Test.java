package io.github.hanbernate.jsonbom.jackson;


import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.BomOrValue;
import io.github.hanbernate.jsonbom.api.Type;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("jackson3 support test")
public class Jackson3Test {
    JsonMapper jsonMapper;
    public Jackson3Test(){
        this.jsonMapper = JsonMapper.builder()
            .addModule(new SimpleModule()
                    .addDeserializer(Bom.class, new Jackson3Deserializer()))

            .build();
    }

    @Test
    public void testDeserialize() throws IOException {
        String str = "{\"author\":\"lmh\", \"artifact\":{\"name\":\"jsonbom\", \"version\":null}}";
        Bom bom = jsonMapper.readValue(str, Bom.class);
        BomOrValue author= bom.get("author");
        assertEquals(Type.VALUE, author.getType());
        assertEquals("lmh", author.value());

        BomOrValue artifact = bom.get("artifact");
        assertEquals(Type.BOM, artifact.getType());
        assertEquals("jsonbom", artifact.get("name").value());
        assertTrue(artifact.bom().containsKey("version"));
        assertEquals(Type.VALUE, artifact.bom().get("version").getType());
    }

    @Test
    public void testDeserializeEmptyString() throws IOException {
        String str = "{\"name\":\"\", \"title\":\"hello\"}";
        Bom bom = jsonMapper.readValue(str, Bom.class);
        assertEquals(2, bom.size());
        assertEquals(BomOrValue.EMPTY, bom.get("name"));
        BomOrValue title = bom.get("title");
        assertEquals(Type.VALUE, title.getType());
        assertEquals("hello", title.value());
    }

    @Test
    public void testDeserializeEmptyNestedObject() throws IOException {
        String str = "{\"outer\":{\"inner\":{}}}";
        Bom bom = jsonMapper.readValue(str, Bom.class);
        BomOrValue outer = bom.get("outer");
        assertEquals(Type.BOM, outer.getType());
        BomOrValue inner = outer.bom().get("inner");
        assertEquals(Type.BOM, inner.getType());
        assertEquals(0, inner.bom().size());
    }

    @Test
    public void testDeserializeEmptyObject() throws IOException {
        Bom bom = jsonMapper.readValue("{}", Bom.class);
        assertEquals(0, bom.size());
    }

    @Test
    public void testJsonProperty() throws NoSuchFieldException {
        JacksonNameParser parser = new JacksonNameParser();
        String fieldName = parser.apply(ObjWithAlias.class.getDeclaredField("name"));
        assertEquals("fullName", fieldName);
    }


    public class ObjWithAlias{
        @JsonProperty("fullName")
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
