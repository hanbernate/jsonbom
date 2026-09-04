package io.github.hanbernate.jsonbom.example.mcp;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import io.github.hanbernate.jsonbom.spring.JsonBomMCPBeanPostProcesser;

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
