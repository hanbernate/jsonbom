package io.github.hanbernate.jsonbom.example.mcp;

import java.util.Map;

import org.reactivestreams.Publisher;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.JsonBomMapper;
import io.github.hanbernate.jsonbom.jackson.Jackson3Deserializer;
import io.github.hanbernate.jsonbom.jackson.JacksonNameParser;
import io.github.hanbernate.jsonbom.spring.ReactorJsonBomMapper;
import lombok.Data;
import reactor.core.publisher.Mono;
import tools.jackson.databind.annotation.JsonDeserialize;

@Service
public class McpToolService {
    JsonBomMapper mapper;

    public McpToolService(){
    
        ReactorJsonBomMapper mapper = new ReactorJsonBomMapper();
        mapper.setNameParser(new JacksonNameParser());
        this.mapper = mapper;

    }

    @Tool(description = "Query grades of student by registryNum")
    public Mono<Response> queryGrade(@ToolParam(description = "query request", required = true)Request request, @ToolParam(required = false) int userId){
        Map<String, Publisher<?>> models = Map.of("lesson", Mono.just("math"), "score", Mono.just(98));
        return Mono.from(mapper.map(Mono.just(request.getBom()), Response.class, models));
    }

    @Data
    public static class Request{
        @ToolParam(description = "registryNum of the student")
        Long registryNum = 0L;

        @ToolParam(description = "response bom")
        @io.github.hanbernate.jsonbom.api.BomType(Response.class)
        @JsonDeserialize(using=Jackson3Deserializer.class)
        Bom bom;

    }

    @Data
    public static class Response{
        @JsonPropertyDescription("lesson name")
        String lesson;

        @JsonPropertyDescription("the score of the lesson")
        Integer score;
    }

}
