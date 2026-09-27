package io.github.hanbernate.jsonbom.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.example.handler.KwhValueHandler;
import io.github.hanbernate.jsonbom.example.model.Kwh;
import io.github.hanbernate.jsonbom.example.model.MeterReport;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * End-to-end tests of both orchestration flows.
 * <p>
 * The repositories are plain, side-effect-free classes, so they are wired
 * directly instead of being mocked. The orchestrator keeps its collaborators
 * package-private, which lets this same-package test inject them without
 * reflection.
 */
@DisplayName("MeterReportOrchestrator tests")
class MeterReportOrchestratorTest {

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

    private MeterReportOrchestrator orchestrator;
    private ObjectMapper jsonMapper;

    @BeforeEach
    void setUp() {
        ReactorJsonBomMapper mapper = new ReactorJsonBomMapper();
        mapper.setNameParser(new JacksonNameParser());
        mapper.registerValueHandler(Kwh.class, new KwhValueHandler());

        orchestrator = new MeterReportOrchestrator();
        orchestrator.jsonBomMapper = mapper;
        orchestrator.deviceSnapshotRepository = new DeviceSnapshotRepository();
        orchestrator.readingRepository = new ReadingRepository();
        orchestrator.kwhRepository = new KwhRepository();
        orchestrator.rawPayloadRepository = new RawPayloadRepository();

        jsonMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Bom.class, new JacksonDeserializer());
        jsonMapper.registerModule(module);
    }

    private Mono<Bom> request(String json) throws IOException {
        return Mono.just(jsonMapper.readValue(json, Bom.class));
    }

    private void assertFullReport(MeterReport report) {
        assertEquals("D-1", report.getDeviceId());
        assertEquals("M-9", report.getDeviceModel());

        assertNotNull(report.getReadings());
        assertEquals(3, report.getReadings().size());

        assertNotNull(report.getReadingSet());
        assertEquals(3, report.getReadingSet().size());

        assertNotNull(report.getAlerts());
        assertEquals(2, report.getAlerts().length);
        assertEquals(99.0d, report.getAlerts()[0].getValue(), 1e-9);

        assertNotNull(report.getKwh());
        assertEquals(0, new BigDecimal("153.46").compareTo(report.getKwh().getValue()));
        assertEquals("kWh", report.getKwh().getUnit());

        assertNotNull(report.getRaw());
        assertEquals("0xA1B2C3", report.getRaw().getHex());
        assertEquals(3, report.getRaw().getLength());
    }

    @Test
    @DisplayName("all requested fields: Flux -> List/Set, Mono -> array, registered handler, valueNode")
    void getMeterReport_allFields() throws IOException {
        StepVerifier.create(orchestrator.getMeterReport(request(ALL_FIELDS), Mono.just("D-1")))
                .assertNext(this::assertFullReport)
                .verifyComplete();
    }

    @Test
    @DisplayName("unrequested fields are left null")
    void getMeterReport_partialFields() throws IOException {
        String partial = "{\"deviceId\":\"\",\"kwh\":\"\"}";

        StepVerifier.create(orchestrator.getMeterReport(request(partial), Mono.just("D-1")))
                .assertNext(report -> {
                    assertEquals("D-1", report.getDeviceId());
                    assertNotNull(report.getKwh());
                    assertNull(report.getDeviceModel());
                    assertNull(report.getReadings());
                    assertNull(report.getReadingSet());
                    assertNull(report.getAlerts());
                    assertNull(report.getRaw());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("heterogeneous flow expands a MeterSnapshot into a MeterReport")
    void getMeterReportViaSnapshot_allFields() throws IOException {
        StepVerifier.create(orchestrator.getMeterReportViaSnapshot(request(ALL_FIELDS), Mono.just("D-1")))
                .assertNext(this::assertFullReport)
                .verifyComplete();
    }

    @Test
    @DisplayName("heterogeneous flow only populates what was requested")
    void getMeterReportViaSnapshot_partialFields() throws IOException {
        StepVerifier.create(orchestrator.getMeterReportViaSnapshot(request("{\"kwh\":\"\"}"), Mono.just("D-1")))
                .assertNext(report -> {
                    assertNotNull(report.getKwh());
                    assertNull(report.getDeviceId());
                    assertNull(report.getReadings());
                    assertNull(report.getAlerts());
                    assertNull(report.getRaw());
                })
                .verifyComplete();
    }
}
