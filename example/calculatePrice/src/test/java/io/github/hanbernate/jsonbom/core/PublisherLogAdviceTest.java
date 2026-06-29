package io.github.hanbernate.jsonbom.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PublisherLogAdviceTest.TestConfig.class)
@DisplayName("PublisherLogAdvice tests")
class PublisherLogAdviceTest {

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        PublisherLogAdvice publisherLogAdvice() {
            return new PublisherLogAdvice();
        }

        @Bean
        TestService testService() {
            return new TestService();
        }
    }

    static class TestService {
        @PublisherLog
        Mono<String> returnMono(Object arg) {
            return Mono.just("returned");
        }

        @PublisherLog
        Mono<String> returnMonoEmpty(Object arg) {
            return Mono.empty();
        }

        @PublisherLog
        String returnObj(Object arg) {
            return "returned";
        }
    }

    @Autowired
    private TestService testService;

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(PublisherLogAdvice.class);
        logger.setLevel(Level.DEBUG);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void detachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(PublisherLogAdvice.class);
        logger.detachAppender(listAppender);
    }

    // ============ arg: Mono.just ============

    @Test
    @DisplayName("Mono.just arg, Mono.just return")
    void monoJustArg_monoJustReturn() throws Exception {
        StepVerifier.create(testService.returnMono(Mono.just("hello")))
                .expectNext("returned")
                .verifyComplete();
        String log = captureLogJson();
        assertLogField(log, "method", "\"returnMono\"");
        assertLogField(log, "args", "{\"arg\":\"hello\"}");
        assertLogField(log, "result", "\"returned\"");
    }

    @Test
    @DisplayName("Mono.just arg, Mono.empty return")
    void monoJustArg_monoEmptyReturn() throws Exception {
        StepVerifier.create(testService.returnMonoEmpty(Mono.just("hello")))
                .verifyComplete();
        String log = captureLogJson();
        assertLogField(log, "method", "\"returnMonoEmpty\"");
        assertLogField(log, "args", "{\"arg\":\"hello\"}");
        assertLogField(log, "result", "null");
    }

    @Test
    @DisplayName("Mono.just arg, Object return")
    void monoJustArg_objReturn() throws Exception {
        assertEquals("returned", testService.returnObj(Mono.just("hello")));
        String log = captureLogJson();
        assertLogField(log, "method", "\"returnObj\"");
        assertLogField(log, "args", "{\"arg\":\"hello\"}");
        assertLogField(log, "result", "\"returned\"");
    }

    // ============ arg: Mono.empty ============

    @Test
    @DisplayName("Mono.empty arg, Mono.just return")
    void monoEmptyArg_monoJustReturn() throws Exception {
        StepVerifier.create(testService.returnMono(Mono.empty()))
                .expectNext("returned")
                .verifyComplete();
        String log = captureLogJson();
        assertLogField(log, "method", "\"returnMono\"");
        assertLogField(log, "args", "{\"arg\":null}");
        assertLogField(log, "result", "\"returned\"");
    }

    @Test
    @DisplayName("Mono.empty arg, Mono.empty return")
    void monoEmptyArg_monoEmptyReturn() throws Exception {
        StepVerifier.create(testService.returnMonoEmpty(Mono.empty()))
                .verifyComplete();
        String log = captureLogJson();
        assertLogField(log, "method", "\"returnMonoEmpty\"");
        assertLogField(log, "args", "{\"arg\":null}");
        assertLogField(log, "result", "null");
    }

    @Test
    @DisplayName("Mono.empty arg, Object return")
    void monoEmptyArg_objReturn() throws Exception {
        assertEquals("returned", testService.returnObj(Mono.empty()));
        String log = captureLogJson();
        assertLogField(log, "method", "\"returnObj\"");
        assertLogField(log, "args", "{\"arg\":null}");
        assertLogField(log, "result", "\"returned\"");
    }

    // ============ arg: plain Object ============

    @Test
    @DisplayName("Object arg, Mono.just return")
    void objArg_monoJustReturn() throws Exception {
        StepVerifier.create(testService.returnMono("plain"))
                .expectNext("returned")
                .verifyComplete();
        String log = captureLogJson();
        assertLogField(log, "method", "\"returnMono\"");
        assertLogField(log, "args", "{\"arg\":\"plain\"}");
        assertLogField(log, "result", "\"returned\"");
    }

    @Test
    @DisplayName("Object arg, Mono.empty return")
    void objArg_monoEmptyReturn() throws Exception {
        StepVerifier.create(testService.returnMonoEmpty("plain"))
                .verifyComplete();
        String log = captureLogJson();
        assertLogField(log, "method", "\"returnMonoEmpty\"");
        assertLogField(log, "args", "{\"arg\":\"plain\"}");
        assertLogField(log, "result", "null");
    }

    @Test
    @DisplayName("Object arg, Mono.empty return")
    void objArg_objReturn() throws Exception {
        assertEquals("returned", testService.returnObj("plain"));
        String log = captureLogJson();
        assertLogField(log, "method", "\"returnObj\"");
        assertLogField(log, "args", "{\"arg\":\"plain\"}");
        assertLogField(log, "result", "\"returned\"");
    }

    // ============ arg: null ============

    @Test
    @DisplayName("null arg, Mono.just return")
    void nullArg_monoJustReturn() throws Exception {
        StepVerifier.create(testService.returnMono(null))
                .expectNext("returned")
                .verifyComplete();
        String log = captureLogJson();
        assertLogField(log, "method", "\"returnMono\"");
        assertLogField(log, "args", "{\"arg\":null}");
        assertLogField(log, "result", "\"returned\"");
    }

    @Test
    @DisplayName("null arg, Mono.empty return")
    void nullArg_monoEmptyReturn() throws Exception {
        StepVerifier.create(testService.returnMonoEmpty(null))
                .verifyComplete();
        String log = captureLogJson();
        assertLogField(log, "method", "\"returnMonoEmpty\"");
        assertLogField(log, "args", "{\"arg\":null}");
        assertLogField(log, "result", "null");
    }

    @Test
    @DisplayName("null arg, Object return")
    void nullArg_objReturn() throws Exception {
        assertEquals("returned", testService.returnObj(null));
        String log = captureLogJson();
        assertLogField(log, "method", "\"returnObj\"");
        assertLogField(log, "args", "{\"arg\":null}");
        assertLogField(log, "result", "\"returned\"");
    }

    // ---- helpers ----

    private String captureLogJson() {
        assertEquals(1, listAppender.list.size());
        String msg = listAppender.list.get(0).getFormattedMessage();

        return msg;
    }

    private static void assertLogField(String logJson, String field, String expectedJson) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertEquals(mapper.readTree(expectedJson), mapper.readTree(logJson).get(field));
    }
}
