package io.github.hanbernate.jsonbom.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.BomMapping;
import io.github.hanbernate.jsonbom.example.handler.KwhValueHandler;
import io.github.hanbernate.jsonbom.example.model.Kwh;
import io.github.hanbernate.jsonbom.example.model.MeterReport;
import io.github.hanbernate.jsonbom.example.model.Reading;
import io.github.hanbernate.jsonbom.jackson.JacksonDeserializer;
import io.github.hanbernate.jsonbom.jackson.JacksonNameParser;
import io.github.hanbernate.jsonbom.spring.ReactorJsonBomMapper;
import lombok.Data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@code genericType} tells the schema factory which class the elements of a
 * {@code List} / {@code Set} / array field are, so each element can be built
 * independently. Without it, the element type is inferred from the field's
 * generic signature.
 */
@DisplayName("genericType resolution for collection / array responses")
class GenericTypeTest {

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
    @DisplayName("genericType = Reading resolves the element type of a List fed by a Flux")
    void genericTypeResolvesListElement() throws IOException {
        Flux<Reading> flux = Flux.just(new Reading("t1", 1.0d), new Reading("t2", 2.0d));
        Map<String, Publisher<?>> models = Map.<String, Publisher<?>>of("readings", flux);

        StepVerifier.create(mapper.map(request("{\"readings\":\"\"}"), MeterReport.class, models))
                .assertNext(report -> {
                    assertNotNull(report.getReadings());
                    assertEquals(2, report.getReadings().size());
                    assertEquals("t1", report.getReadings().get(0).getReadAt());
                    assertEquals(1.0d, report.getReadings().get(0).getValue(), 1e-9);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("a nested request BOM is applied to every element (partial element selection)")
    void nestedBomSelectsElementFields() throws IOException {
        Flux<Reading> flux = Flux.just(new Reading("t1", 1.0d), new Reading("t2", 2.0d));
        Map<String, Publisher<?>> models = Map.<String, Publisher<?>>of("readings", flux);

        StepVerifier.create(mapper.map(request("{\"readings\":{\"readAt\":\"\"}}"), MeterReport.class, models))
                .assertNext(report -> {
                    assertEquals(2, report.getReadings().size());
                    Reading first = report.getReadings().get(0);
                    assertEquals("t1", first.getReadAt());
                    assertEquals(0.0d, first.getValue(), 1e-9);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("genericType = Reading resolves the element type of an array fed by a Mono")
    void genericTypeResolvesArrayElement() throws IOException {
        Map<String, Publisher<?>> models = Map.<String, Publisher<?>>of(
                "alerts", Mono.just(new Reading[]{new Reading("a1", 99.0d), new Reading("a2", 98.4d)}));

        StepVerifier.create(mapper.map(request("{\"alerts\":{\"readAt\":\"\"}}"), MeterReport.class, models))
                .assertNext(report -> {
                    assertNotNull(report.getAlerts());
                    assertEquals(2, report.getAlerts().length);
                    assertEquals("a1", report.getAlerts()[0].getReadAt());
                    assertEquals(0.0d, report.getAlerts()[0].getValue(), 1e-9);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("element type is inferred from the field signature when genericType is absent")
    void genericTypeInferredFromSignature() throws IOException {
        Flux<Reading> flux = Flux.just(new Reading("t1", 1.0d), new Reading("t2", 2.0d));
        Map<String, Publisher<?>> models = Map.<String, Publisher<?>>of("rawReadings", flux);

        StepVerifier.create(mapper.map(request("{\"rawReadings\":\"\"}"), RawListModel.class, models))
                .assertNext(model -> {
                    assertNotNull(model.getRawReadings());
                    assertEquals(2, model.getRawReadings().size());
                })
                .verifyComplete();
    }

    /** No {@code genericType}: the element type comes from {@code List<Reading>}. */
    @Data
    public static class RawListModel {

        @BomMapping("rawReadings")
        private List<Reading> rawReadings;
    }
}
