package io.github.hanbernate.jsonbom.victools;

import java.util.List;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.util.json.schema.SpringAiSchemaModule;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.BomMapping;
import io.github.hanbernate.jsonbom.api.ValueHandler;
import io.github.hanbernate.jsonbom.jackson.Jackson3Deserializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("BomPropertyDefinitionProvider test")
class BomPropertyDefinitionProviderTest {

    SchemaGenerator generator;

    @BeforeEach
    void setUp() {
        JacksonSchemaModule jacksonModule = new JacksonSchemaModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED);
		Swagger2Module openApiModule = new Swagger2Module();
		SpringAiSchemaModule springAiSchemaModule = new SpringAiSchemaModule();
        SchemaGeneratorConfigBuilder schemaGeneratorConfigBuilder = new SchemaGeneratorConfigBuilder(
				SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
			.with(jacksonModule)
			.with(openApiModule)
			.with(springAiSchemaModule)
			.with(Option.EXTRA_OPEN_API_FORMAT_VALUES)
			.with(Option.PLAIN_DEFINITION_KEYS)
            .without(Option.SCHEMA_VERSION_INDICATOR);
        schemaGeneratorConfigBuilder.forFields()
            .withCustomDefinitionProvider(new BomPropertyDefinitionProvider());
        this.generator = new SchemaGenerator(schemaGeneratorConfigBuilder.build());
    }

    @Test
    public void test() throws NoSuchFieldException, SecurityException{
        String expectedStr = """
 {
  "type": "object",
  "properties": {
    "bom": {
      "type": "object",
      "properties": {
        "valueNode": {
          "type": "string"
        },
        "valuehander": {
          "type": "string"
        },
        "premitive": {
          "type": "string"
        },
        "boxed": {
          "type": "string"
        },
        "enumNode": {
          "type": "string"
        },
        "sublist": {
          "type": "string"
        },
        "grade": {
          "type": "object",
          "properties": {
            "lesson": {
              "type": "string"
            },
            "score": {
              "type": "string"
            }
          }
        }
      },
      "description": "response bom"
    },
    "registryNum": {
      "type": "integer",
      "format": "int64",
      "description": "registryNum of the student"
    }
  },
  "required": [
    "bom",
    "registryNum"
  ]
}
        """;
        JsonNode expect = JacksonUtils.getDefaultJsonMapper().readTree(expectedStr);
        ObjectNode actual = this.generator.generateSchema(Request.class);
        assertEquals(expect, actual);
    }

    public static class Request{
        @ToolParam(description = "registryNum of the student")
        Long registryNum = 0L;

        public Long getRegistryNum(){
            return this.registryNum;
        }

        public void setRegistryNum(Long registryNum){
            this.registryNum = registryNum;
        }

        @ToolParam(description = "response bom")
        @io.github.hanbernate.jsonbom.api.BomType(Response.class)
        @JsonDeserialize(using=Jackson3Deserializer.class)
        Bom bom;

        public Bom getBom(){
            return this.bom;
        }

        public void setBom(Bom bom){
            this.bom = bom;
        }

    }

public static class Response{
        @BomMapping (value="valueNode", valueNode = true)
        Response valueNode;

        @BomMapping (value="valueHandler", valueHandler = DoNotingValueHandler.class)
        Object valuehander;

        int premitive;

        Integer boxed;

        io.github.hanbernate.jsonbom.api.Type enumNode;

        List<Integer> sublist;

        List<Grade> grade;

        public Response getValueNode(){
            return this.valueNode;
        }

        public void setValueNode(Response valueNode){
            this.valueNode = valueNode;
        }

        public Object getValuehander(){
            return this.valuehander;
        }

        public void setValuehander(Object valuehander){
            this.valuehander = valuehander;
        }

        public int getPremitive(){
            return this.premitive;
        }

        public void setPremitive(int premitive){
            this.premitive = premitive;
        }

        public Integer getBoxed(){
            return this.boxed;
        }

        public void setBoxed(Integer boxed){
            this.boxed = boxed;
        }

        public io.github.hanbernate.jsonbom.api.Type getEnumNode(){
            return this.enumNode;
        }

        public void setEnumNode(io.github.hanbernate.jsonbom.api.Type enumNode){
            this.enumNode = enumNode;
        }

        public List<Integer> getSublist(){
            return this.sublist;
        }

        public void setSublist(List<Integer> sublist){
            this.sublist = sublist;
        }

        public List<Grade> getGrade(){
            return this.grade;
        }

        public void setGrade(List<Grade> grade){
            this.grade = grade;
        }
    }

    public static class DoNotingValueHandler implements ValueHandler<Object>{
        @Override
        public Object apply(Object model, String bomValue) {
            return model;
        }
    }

    public static class Grade{
        String lesson;

        String score;

        public String getLesson(){
            return this.lesson;
        }

        public void setLesson(String lesson){
            this.lesson = lesson;
        }

        public String getScore(){
            return this.score;
        }

        public void setScore(String score){
            this.score = score;
        }
    }
}
