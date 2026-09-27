package io.github.hanbernate.jsonbom.example;

import io.github.hanbernate.jsonbom.api.JsonBomMapper;
import io.github.hanbernate.jsonbom.example.handler.KwhValueHandler;
import io.github.hanbernate.jsonbom.example.model.Kwh;
import io.github.hanbernate.jsonbom.jackson.JacksonNameParser;
import io.github.hanbernate.jsonbom.spring.ReactorJsonBomMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link JsonBomMapper} used by this example.
 * <p>
 * Besides the Jackson based name parser, this configuration registers a
 * <em>default</em> {@link io.github.hanbernate.jsonbom.api.ValueHandler} for the
 * {@link Kwh} return type. Any field whose declared type is {@code Kwh} is then
 * automatically treated as a leaf value and handled by {@link KwhValueHandler},
 * without having to reference the handler from {@code @BomMapping} on every field.
 */
@Configuration
@ComponentScan(basePackageClasses = AppConfig.class)
public class AppConfig {

    @Bean
    public JsonBomMapper jsonBomMapper() {
        ReactorJsonBomMapper mapper = new ReactorJsonBomMapper();
        mapper.setNameParser(new JacksonNameParser());
        // Registered by return type: every Kwh field is handled by KwhValueHandler.
        mapper.registerValueHandler(Kwh.class, new KwhValueHandler());
        return mapper;
    }
}
