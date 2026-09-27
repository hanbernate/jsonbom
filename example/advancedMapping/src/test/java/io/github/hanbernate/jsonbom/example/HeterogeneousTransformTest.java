package io.github.hanbernate.jsonbom.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.Type;
import io.github.hanbernate.jsonbom.example.handler.KwhValueHandler;
import io.github.hanbernate.jsonbom.example.model.Kwh;
import io.github.hanbernate.jsonbom.example.model.MeterReport;
import io.github.hanbernate.jsonbom.example.model.MeterSnapshot;
import io.github.hanbernate.jsonbom.example.model.Reading;
import io.github.hanbernate.jsonbom.example.repository.DeviceSnapshotRepository;
import io.github.hanbernate.jsonbom.example.repository.KwhRepository;
import io.github.hanbernate.jsonbom.example.repository.RawPayloadRepository;
import io.github.hanbernate.jsonbom.example.repository.ReadingRepository;
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
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers {@code map(targetBom, targetType, modelType, sourceModels)} together
 * with the two contracts it relies on:
 * <ul>
 *     <li>{@code transformBom} re-roots every entry by the first path segment
 *         of the matching field, so the transformed BOM is keyed by model name
 *         (and cannot be used for the final map by field name);</li>
 *     <li>each source-model child name must equal the {@code path[0]} of the
 *         corresponding target field, and select an entry of {@code sourceModels}.</li>
 * </ul>
 */
@DisplayName("heterogeneous model transformation (MeterReport <- MeterSnapshot)")
class HeterogeneousTransformTest {

    private static final String ALL_FIELDS = """
            {
              "deviceId": "",
              "deviceModel": "",
              "readings": "",
              "readingSet": "",
              "alerts": "",
              "kwh": "",
              "raw": ""
            }
            """;

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

    private Bom parse(String json) throws IOException {
        return jsonMapper.readValue(json, Bom.class);
    }

    private Map<String, Publisher<?>> sourceModels() {
        Map<String, Publisher<?>> sourceModels = new HashMap<>();
        sourceModels.put("device", new DeviceSnapshotRepository().findById(
                Mono.just(Bom.createWithEmptyValue("deviceId", "model")), Mono.just("D-1")));

        Flux<Reading> readings = new ReadingRepository().findByDeviceId(Mono.just(new Bom()), Mono.just("D-1"));
        sourceModels.put("readings", readings);
        sourceModels.put("readingSet", readings);
        sourceModels.put("alerts", new ReadingRepository().findAlerts(Mono.just(new Bom()), Mono.just("D-1")));
        sourceModels.put("kwh", new KwhRepository().findLatest(Mono.just(new Bom()), Mono.just("D-1")));
        sourceModels.put("raw", new RawPayloadRepository().load(Mono.just(new Bom()), Mono.just("D-1")));
        return sourceModels;
    }

    @Test
    @DisplayName("transformBom re-roots every entry by the first path segment")
    void transformBomReRootsByPathRoot() throws IOException {
        Bom transformed = mapper.getBomAdapter().transformBom(parse(ALL_FIELDS), MeterReport.class);

        assertEquals(Set.of("device", "readings", "readingSet", "alerts", "kwh", "raw"), transformed.keySet());
        assertEquals(Type.BOM, transformed.get("device").getType());
        assertEquals(Type.VALUE, transformed.get("readings").getType());
        assertEquals(Type.VALUE, transformed.get("kwh").getType());
    }

    @Test
    @DisplayName("nested entries keep their real field names inside the re-rooted branch")
    void transformBomKeepsNestedFieldNames() throws IOException {
        Bom transformed = mapper.getBomAdapter().transformBom(parse(ALL_FIELDS), MeterReport.class);

        assertEquals(Set.of("deviceId", "model"), transformed.getBom("device").keySet());
    }

    @Test
    @DisplayName("the mapper expands MeterSnapshot into MeterReport")
    void expandsSnapshotIntoTarget() throws IOException {
        StepVerifier.create(mapper.map(request(ALL_FIELDS), MeterReport.class, MeterSnapshot.class, sourceModels()))
                .assertNext(report -> {
                    assertEquals("D-1", report.getDeviceId());
                    assertEquals("M-9", report.getDeviceModel());
                    assertEquals(3, report.getReadings().size());
                    assertEquals(3, report.getReadingSet().size());
                    assertEquals(2, report.getAlerts().length);
                    assertEquals(0, new BigDecimal("153.46").compareTo(report.getKwh().getValue()));
                    assertEquals("0xA1B2C3", report.getRaw().getHex());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("only requested target fields are populated")
    void respectsRequestedFields() throws IOException {
        StepVerifier.create(mapper.map(
                        request("{\"deviceId\":\"\"}"), MeterReport.class, MeterSnapshot.class, sourceModels()))
                .assertNext(report -> {
                    assertEquals("D-1", report.getDeviceId());
                    assertNull(report.getDeviceModel());
                    assertNull(report.getReadings());
                    assertNull(report.getReadingSet());
                    assertNull(report.getAlerts());
                    assertNull(report.getKwh());
                    assertNull(report.getRaw());
                })
                .verifyComplete();
    }
}
