package io.github.hanbernate.jsonbom.example.mcp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.server.RouterFunction;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.hanbernate.jsonbom.spring.JsonBomMCPBeanPostProcesser;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebFluxStreamableServerTransportProvider;

@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    @Bean
    public JsonBomMCPBeanPostProcesser jsonBomMCPBeanPostProcesser() {
        return new JsonBomMCPBeanPostProcesser();
    }
}
