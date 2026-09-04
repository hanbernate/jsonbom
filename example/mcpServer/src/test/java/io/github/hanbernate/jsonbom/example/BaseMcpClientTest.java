package io.github.hanbernate.jsonbom.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.hanbernate.jsonbom.example.mcp.McpServerApplication;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;

import org.springframework.ai.mcp.client.webflux.transport.WebClientStreamableHttpTransport;
import org.springframework.ai.util.JacksonUtils;

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import tools.jackson.databind.JsonNode;

@SpringBootTest(classes = {McpServerApplication.class}, webEnvironment = WebEnvironment.RANDOM_PORT)
abstract class BaseMcpClientTest {

    @LocalServerPort // 2. 注入实际分配的随机端口
    private int port;

    protected void test(){
        McpSyncClient client = buildSyncClient();
        String toolName = testListToolsAndGetToolName(client);
        testCall(client, toolName);
        closeClient(client);
    }

    protected McpSyncClient buildSyncClient(){
        var transport = WebClientStreamableHttpTransport.builder(WebClient.builder().baseUrl("http://localhost:" + port ))
            .build();
        var client = McpClient.sync(transport).build();
		client.initialize();
		client.ping();
        return client;
    }
    public String testListToolsAndGetToolName(McpSyncClient client){

		// List and demonstrate tools
		ListToolsResult toolsList = client.listTools();
		System.out.println("Available Tools = " + toolsList);
        assertNotEquals(0, toolsList.tools().size());
        Tool tool = toolsList.tools().get(0);
        Map<String, Object> inputSchema = tool.inputSchema();
        List<String> required = (List<String>) inputSchema.get("required");
        assertEquals(1, required.size());
        assertEquals("request", required.get(0));
        Map<String, Object> properties = (Map<String, Object>)inputSchema.get("properties");

        Map<String, Object> userId = (Map<String, Object>) properties.get("userId");
        assertEquals("integer", userId.get("type"));

        Map<String, Object> requestSchema = (Map<String, Object>) properties.get("request");
        assertEquals("object", requestSchema.get("type"));
        assertEquals("query request", requestSchema.get("description"));
        List<String> requestRequired = (List<String>) requestSchema.get("required");
        assertEquals(2, requestRequired.size());
        assertTrue(requestRequired.contains("bom"));
        Map<String, Object> requestProperties = (Map<String, Object>) requestSchema.get("properties");

        Map<String, Object> registryNum = (Map<String, Object>) requestProperties.get("registryNum");
        assertEquals("integer", registryNum.get("type"));
        assertEquals("int64", registryNum.get("format"));
        assertEquals("registryNum of the student", registryNum.get("description"));

        assertBom(requestProperties.get("bom"));

        return tool.name();

        

    }

    protected void testCall(McpSyncClient client, String toolName){
        String requestJson = """
            {
                "registryNum":0,
                "bom":{
                    "lesson":""
                }
            }
                """;
        JsonNode request = JacksonUtils.getDefaultJsonMapper().readTree(requestJson);
        Map<String, Object> args = Map.of("request", request, "userId", 0);
        CallToolRequest callToolRequest = new CallToolRequest(toolName, args, null);
        CallToolResult callToolResult = client.callTool(callToolRequest);
        Map<String, Object> result = (Map<String, Object>)callToolResult.structuredContent();
        assertNotNull(result.get("lesson"));
        assertNull(result.get("score"));
    }

    protected void closeClient(McpSyncClient client){
		client.closeGracefully();
    }

    private void assertBom(Object obj){
        Map<String, Object> bom = (Map<String, Object>) obj;
        assertEquals("object", bom.get("type"));
        assertNull(bom.get("required"));
        assertEquals("response bom", bom.get("description"));
        
        Map<String, Object> bomProperties = (Map<String, Object>) bom.get("properties");

        Map<String, Object> lesson = (Map<String, Object>) bomProperties.get("lesson");
        assertEquals("string", lesson.get("type"));
        assertEquals("lesson name", lesson.get("description"));

        Map<String, Object> score = (Map<String, Object>) bomProperties.get("score");
        assertEquals("string", score.get("type"));
        assertEquals("the score of the lesson", score.get("description"));
    }
}
