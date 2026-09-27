package io.github.hanbernate.jsonbom.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.JsonBomException;
import io.github.hanbernate.jsonbom.example.handler.KwhValueHandler;
import io.github.hanbernate.jsonbom.example.model.Kwh;
import io.github.hanbernate.jsonbom.example.model.MeterReport;
import io.github.hanbernate.jsonbom.example.model.Reading;
import io.github.hanbernate.jsonbom.jackson.JacksonDeserializer;
import io.github.hanbernate.jsonbom.jackson.JacksonNameParser;
import io.github.hanbernate.jsonbom.spring.ReactorJsonBomMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Collection responses depend on the <em>model publisher</em> kind as much as on
 * the field type:
 * <ul>
 *     <li>{@code Flux} &rarr; {@code List} / {@code Set} (a {@code Set}
 *         de-duplicates by {@code equals}); one {@code Flux} may feed several
 *         collection fields;</li>
 *     <li>{@code Mono<?>} of an array &rarr; array response;</li>
 *     <li>{@code Flux} &rarr; array is rejected with a {@link JsonBomException}.</li>
 * </ul>
 */
@DisplayName("Set and array responses")
class SetAndArrayResponseTest {

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
    @DisplayName("one Flux can feed both a List field and a Set field")
    void oneFluxFeedsListAndSet() throws IOException {
        Flux<Reading> flux = Flux.just(
                new Reading("t1", 1.0d), new Reading("t2", 2.0d), new Reading("t3", 3.0d));

        Map<String, Publisher<?>> models = new HashMap<>();
        models.put("readings", flux);
        models.put("readingSet", flux);

        StepVerifier.create(mapper.map(request("{\"readings\":\"\",\"readingSet\":\"\"}"), MeterReport.class, models))
                .assertNext(report -> {
                    assertEquals(3, report.getReadings().size());
                    assertEquals(3, report.getReadingSet().size());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("a Set response de-duplicates equal elements while the List keeps them")
    void setDeDuplicates() throws IOException {
        Flux<Reading> flux = Flux.just(new Reading("t1", 1.0d), new Reading("t1", 1.0d));

        Map<String, Publisher<?>> models = new HashMap<>();
        models.put("readings", flux);
        models.put("readingSet", flux);

        StepVerifier.create(mapper.map(request("{\"readings\":\"\",\"readingSet\":\"\"}"), MeterReport.class, models))
                .assertNext(report -> {
                    assertEquals(2, report.getReadings().size());
                    assertEquals(1, report.getReadingSet().size());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("a Mono of an array is the supported way to feed an array response")
    void monoArrayFeedsArrayResponse() throws IOException {
        Map<String, Publisher<?>> models = Map.<String, Publisher<?>>of(
                "alerts", Mono.just(new Reading[]{new Reading("a1", 9.0d), new Reading("a2", 8.0d)}));

        StepVerifier.create(mapper.map(request("{\"alerts\":\"\"}"), MeterReport.class, models))
                .assertNext(report -> {
                    assertEquals(2, report.getAlerts().length);
                    assertEquals(9.0d, report.getAlerts()[0].getValue(), 1e-9);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("a Flux cannot be converted to an array response")
    void fluxCannotFeedArray() throws IOException {
        Map<String, Publisher<?>> models = Map.<String, Publisher<?>>of(
                "alerts", Flux.just(new Reading("a1", 9.0d)));

        StepVerifier.create(mapper.map(request("{\"alerts\":\"\"}"), MeterReport.class, models))
                .expectError(JsonBomException.class)
                .verify();
    }
}
