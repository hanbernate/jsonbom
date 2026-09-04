package io.github.hanbernate.jsonbom.victools;

import java.lang.reflect.ParameterizedType;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
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
import io.github.hanbernate.jsonbom.api.BomType;
import io.github.hanbernate.jsonbom.jackson.Jackson3Deserializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                "type":"object",
                "properties":{
                    "bom":{
                        "type":"object",
                        "properties":{
                            "lesson":{
                                "type":"string",
                                "description":"lesson name"
                            },
                            "score":{
                                "type":"string",
                                "description":"the score of the lesson"
                            }
                        },
                        "description":"response bom"
                    },
                    "registryNum":{
                        "type":"integer",
                        "format":"int64",
                        "description":"registryNum of the student"
                    }
                },
                "required":["bom","registryNum"]
            }"
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
        @JsonPropertyDescription("lesson name")
        String lesson;

        @JsonPropertyDescription("the score of the lesson")
        Integer score;

        public String getLesson(){
            return this.lesson;
        }

        public void setLesson(String lesson){
            this.lesson = lesson;
        }

        public Integer getScore(){
            return this.score;
        }

        public void setScore(Integer score){
            this.score = score;
        }
    }
}
