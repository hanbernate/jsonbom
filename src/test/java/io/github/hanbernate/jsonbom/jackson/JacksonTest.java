package io.github.hanbernate.jsonbom.jackson;


import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.BomOrValue;
import io.github.hanbernate.jsonbom.api.Type;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("jackson support test")
public class JacksonTest {

    static ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    public static void init(){
        JsonDeserializer<Bom> deserializer = new JacksonDeserializer();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Bom.class, deserializer);
        objectMapper.registerModule(module);
    }

    @Test
    public void testDeserialize() throws IOException {
        String str = "{\"author\":\"lmh\", \"artifact\":{\"name\":\"jsonbom\", \"version\":null}}";
        Bom bom = objectMapper.readValue(str, Bom.class);
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
