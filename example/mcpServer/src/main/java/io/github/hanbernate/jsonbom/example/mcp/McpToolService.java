package io.github.hanbernate.jsonbom.example.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.core.BomType;
import lombok.Data;

@Service
public class McpToolService {

    @Tool(description = "Query grades of student by registryNum")
    public Response queryGrade(@ToolParam(description = "query request", required = true)Request request, @ToolParam(required = false) int userId){
        return null;
    }

    @Data
    public static class Request{
        @ToolParam(description = "registryNum of the student")
        Long registryNum = 0L;

        @ToolParam(description = "response bom")
        @BomType(Response.class)
        Bom bom;

    }

    @Data
    public static class Response{
        @JsonPropertyDescription("lesson name")
        String lesson;

        @JsonPropertyDescription("the score of the lesson")
        int score;
    }

}
