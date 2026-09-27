package io.github.hanbernate.jsonbom.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.JsonBomMapper;
import io.github.hanbernate.jsonbom.jackson.Jackson3Deserializer;
import io.github.hanbernate.jsonbom.jackson.JacksonNameParser;
import io.github.hanbernate.jsonbom.spring.ReactorJsonBomMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * Example application configuration.
 * <p>
 * Enables AspectJ auto-proxy for {@code @PublisherLog} and wires the Jackson 3
 * {@link JsonMapper} (with {@link Jackson3Deserializer} registered for {@link Bom},
 * so a request body like {@code {"finalPrice":""}} is read straight into a BOM tree)
 * together with the jsonbom {@link JsonBomMapper}.
 */
@Configuration
@EnableAspectJAutoProxy
@ComponentScan(basePackageClasses = AppConfig.class)
public class AppConfig {

    @Bean
    public JsonMapper bomJsonMapper() {
        return JsonMapper.builder()
                .addModule(new SimpleModule()
                        .addDeserializer(Bom.class, new Jackson3Deserializer()))
                .build();
    }

    @Bean
    public JsonBomMapper jsonBomMapper() {
        ReactorJsonBomMapper mapper = new ReactorJsonBomMapper();
        mapper.setNameParser(new JacksonNameParser());
        return mapper;
    }
}
