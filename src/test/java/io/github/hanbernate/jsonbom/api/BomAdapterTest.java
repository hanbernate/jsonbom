package io.github.hanbernate.jsonbom.api;

import io.github.hanbernate.jsonbom.api.model.RegisteredType;
import io.github.hanbernate.jsonbom.api.model.RootType;
import io.github.hanbernate.jsonbom.api.valuehadnler.RegisteredTypeValueHandler;
import io.github.hanbernate.jsonbom.jackson.JacksonDeserializer;
import io.github.hanbernate.jsonbom.jackson.JacksonNameParser;
import io.github.hanbernate.jsonbom.spring.ReactorJsonBomMapper;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonBomMapper test")
public class BomAdapterTest {

    BomAdapter bomAdapter;

    ObjectMapper jsonMapper;

    public BomAdapterTest(){
        ReactorJsonBomMapper mapper = new ReactorJsonBomMapper();
        mapper.setNameParser(new JacksonNameParser());
        mapper.registerValueHandler(RegisteredType.class, new RegisteredTypeValueHandler());
        this.bomAdapter = mapper.getBomAdapter();

        ObjectMapper objectMapper = new ObjectMapper();
        JsonDeserializer<Bom> deserializer = new JacksonDeserializer();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Bom.class, deserializer);
        objectMapper.registerModule(module);
        this.jsonMapper = objectMapper;
    }


    @Test
    public void nest() throws IOException {

        String json = "{\"child\":{\"primitive\":\"\",\"boxed\":\"\",\"string\":\"\"},\"alias\":{\"primitive\":\"\",\"boxed\":\"\",\"type\":\"\"}}";
        Bom bom = jsonMapper.readValue(json, Bom.class);

        Bom modelBom = bomAdapter.transformBom(bom, RootType.class);

        assertEquals(1, modelBom.size());
        BomOrValue modelNode = modelBom.get("model");
        assertEquals(Type.BOM, modelNode.getType());

        assertEquals(4, modelNode.bom().size());
    }

    @Test
    public void test(){
        assertTrue("jsonbom-0.0.1.module.md5".contains(".module"));
    }
}
