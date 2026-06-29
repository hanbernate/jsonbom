package io.github.hanbernate.jsonbom.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

@SpringBootTest(classes = {McpServerApplication.class}, webEnvironment = WebEnvironment.RANDOM_PORT)
public class McpClientTest {

    @LocalServerPort // 2. 注入实际分配的随机端口
    private int port;

    @Test
    public void test(){
        var transport = WebClientStreamableHttpTransport.builder(WebClient.builder().baseUrl("http://localhost:" + port ))
            .build();
        var client = McpClient.sync(transport).build();

		client.initialize();

		client.ping();

		// List and demonstrate tools
		ListToolsResult toolsList = client.listTools();
		System.out.println("Available Tools = " + toolsList);
        assertNotEquals(0, toolsList.tools().size());
        Tool tool = toolsList.tools().get(0);
        JsonSchema inputSchema = tool.inputSchema();
        List<String> required = inputSchema.required();
        assertEquals(1, required.size());
        assertEquals("request", required.get(0));
        Map<String, Object> properties = inputSchema.properties();

        Map<String, Object> userId = (Map<String, Object>) properties.get("userId");
        assertEquals("integer", userId.get("type"));

        Map<String, Object> request = (Map<String, Object>) properties.get("request");
        assertEquals("object", request.get("type"));
        assertEquals("query request", request.get("description"));
        List<String> requestRequired = (List<String>) request.get("required");
        assertEquals(2, requestRequired.size());
        assertTrue(requestRequired.contains("bom"));
        Map<String, Object> requestProperties = (Map<String, Object>) request.get("properties");

        Map<String, Object> registryNum = (Map<String, Object>) requestProperties.get("registryNum");
        assertEquals("integer", registryNum.get("type"));
        assertEquals("int64", registryNum.get("format"));
        assertEquals("registryNum of the student", registryNum.get("description"));

        assertBom(requestProperties.get("bom"));

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
