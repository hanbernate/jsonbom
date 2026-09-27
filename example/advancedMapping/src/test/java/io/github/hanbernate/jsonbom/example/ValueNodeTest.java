package io.github.hanbernate.jsonbom.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.example.handler.KwhValueHandler;
import io.github.hanbernate.jsonbom.example.model.Kwh;
import io.github.hanbernate.jsonbom.example.model.MeterReport;
import io.github.hanbernate.jsonbom.example.model.RawPayload;
import io.github.hanbernate.jsonbom.jackson.JacksonDeserializer;
import io.github.hanbernate.jsonbom.jackson.JacksonNameParser;
import io.github.hanbernate.jsonbom.spring.ReactorJsonBomMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@code valueNode = true} marks a field as an opaque leaf: its schema has no
 * children, so the mapper never introspects the model object.
 * <ul>
 *     <li>a plain value request — {@code {"raw":""}} — passes the model through
 *         untouched;</li>
 *     <li>a nested request BOM — {@code {"raw":{"hex":""}}} — cannot drill into
 *         the object and yields an empty instance instead.</li>
 * </ul>
 */
@DisplayName("valueNode fields")
class ValueNodeTest {

    private ReactorJsonBomMapper mapper;
    private ObjectMapper jsonMapper;

    @BeforeEach
    void setUp() {
        mapper = new ReactorJsonBomMapper();
        mapper.setNameParser(new JacksonNameParser());
        mapper.registerValueHandler(Kwh.class, new KwhValueHandler());

        jsonMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Bom.class, new JacksonDeserializer());
        jsonMapper.registerModule(module);
    }

    private Mono<Bom> request(String json) throws IOException {
        return Mono.just(jsonMapper.readValue(json, Bom.class));
    }

    @Test
    @DisplayName("a plain value request passes the model object through untouched")
    void valueRequestPassesModelThrough() throws IOException {
        RawPayload payload = new RawPayload("0xA1B2C3", 3);
        Map<String, Publisher<?>> models = Map.<String, Publisher<?>>of("raw", Mono.just(payload));

        StepVerifier.create(mapper.map(request("{\"raw\":\"\"}"), MeterReport.class, models))
                .assertNext(report -> assertSame(payload, report.getRaw()))
                .verifyComplete();
    }

    @Test
    @DisplayName("a nested request BOM cannot drill into a valueNode field; an empty object is built")
    void nestedBomBuildsEmptyObject() throws IOException {
        RawPayload payload = new RawPayload("0xA1B2C3", 3);
        Map<String, Publisher<?>> models = Map.<String, Publisher<?>>of("raw", Mono.just(payload));

        StepVerifier.create(mapper.map(request("{\"raw\":{\"hex\":\"\"}}"), MeterReport.class, models))
                .assertNext(report -> {
                    assertNotNull(report.getRaw());
                    assertNotSame(payload, report.getRaw());
                    assertNull(report.getRaw().getHex());
                    assertEquals(0, report.getRaw().getLength());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("a missing model leaves the valueNode field null")
    void missingModelLeavesFieldNull() throws IOException {
        Map<String, Publisher<?>> models = Map.<String, Publisher<?>>of("raw", Mono.empty());

        StepVerifier.create(mapper.map(request("{\"raw\":\"\"}"), MeterReport.class, models))
                .assertNext(report -> assertNull(report.getRaw()))
                .verifyComplete();
    }
}
