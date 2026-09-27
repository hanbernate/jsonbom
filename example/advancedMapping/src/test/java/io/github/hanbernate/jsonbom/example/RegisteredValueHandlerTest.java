package io.github.hanbernate.jsonbom.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.Schema;
import io.github.hanbernate.jsonbom.example.handler.KwhValueHandler;
import io.github.hanbernate.jsonbom.example.model.Kwh;
import io.github.hanbernate.jsonbom.example.model.KwhSample;
import io.github.hanbernate.jsonbom.example.model.MeterReport;
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
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A {@link io.github.hanbernate.jsonbom.api.ValueHandler} can be registered once
 * by <em>response type</em>, so every field of that type is handled without any
 * {@code @BomMapping.valueHandler} on the field.
 */
@DisplayName("registered ValueHandler (by response type)")
class RegisteredValueHandlerTest {

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
    @DisplayName("the handler is attached to the Kwh field schema, making it a leaf")
    void handlerIsAttachedToFieldSchema() {
        Schema<?> kwhSchema = mapper.registerSchemaIfAbsent(MeterReport.class).getChildren().get("kwh");

        assertNotNull(kwhSchema);
        assertTrue(kwhSchema.getValueHandler() instanceof KwhValueHandler);
        assertTrue(kwhSchema.getChildren().isEmpty());
    }

    @Test
    @DisplayName("a raw KwhSample from the repository is converted to Kwh")
    void convertsRawSample() throws IOException {
        Map<String, Publisher<?>> models = Map.<String, Publisher<?>>of(
                "kwh", Mono.just(new KwhSample(new BigDecimal("153.456"))));

        StepVerifier.create(mapper.map(request("{\"kwh\":\"\"}"), MeterReport.class, models))
                .assertNext(report -> {
                    assertNotNull(report.getKwh());
                    assertEquals(0, new BigDecimal("153.46").compareTo(report.getKwh().getValue()));
                    assertEquals("kWh", report.getKwh().getUnit());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("the handler is idempotent: an already converted Kwh is returned as is")
    void acceptsAlreadyConverted() throws IOException {
        Kwh alreadyConverted = new Kwh(new BigDecimal("10.00"), "kWh");
        Map<String, Publisher<?>> models = Map.<String, Publisher<?>>of("kwh", Mono.just(alreadyConverted));

        StepVerifier.create(mapper.map(request("{\"kwh\":\"\"}"), MeterReport.class, models))
                .assertNext(report -> assertSame(alreadyConverted, report.getKwh()))
                .verifyComplete();
    }

    @Test
    @DisplayName("handler unit behaviour")
    void handlerUnit() {
        KwhValueHandler handler = new KwhValueHandler();

        Kwh fromSample = handler.apply(new KwhSample(new BigDecimal("153.456")), null);
        assertNotNull(fromSample);
        assertEquals(0, new BigDecimal("153.46").compareTo(fromSample.getValue()));
        assertEquals("kWh", fromSample.getUnit());

        Kwh original = new Kwh(new BigDecimal("10.00"), "kWh");
        assertSame(original, handler.apply(original, null));

        Kwh fromText = handler.apply(null, "12.3");
        assertNotNull(fromText);
        assertEquals(0, new BigDecimal("12.30").compareTo(fromText.getValue()));

        assertNull(handler.apply(null, ""));
        assertNull(handler.apply(null, null));
    }
}
