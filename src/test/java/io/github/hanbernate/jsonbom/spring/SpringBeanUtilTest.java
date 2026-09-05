package io.github.hanbernate.jsonbom.spring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpringBeanUtil obj2Map test")
class SpringBeanUtilTest {

    private SpringBeanUtil springBeanUtil;

    @BeforeEach
    void setUp() {
        springBeanUtil = new SpringBeanUtil();
    }

    @Test
    void obj2MapShouldExtractMonoFields() {
        Object models = new Object() {
            public Mono<String> getName() { return Mono.just("test"); }
            public Flux<String> getItems() { return Flux.just("a", "b"); }
            public String getNonPublisher() { return "plain"; }
            public Mono<String> getMissing() { return null; }
        };

        Map<String, Publisher<?>> result = springBeanUtil.obj2Map(models);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("name"));
        assertTrue(result.containsKey("items"));
        assertFalse(result.containsKey("nonPublisher"));
        assertFalse(result.containsKey("missing"));
    }

    @Test
    void obj2MapWithEmptyObjectShouldReturnEmptyMap() {
        Object models = new Object() {};

        Map<String, Publisher<?>> result = springBeanUtil.obj2Map(models);
        assertTrue(result.isEmpty());
    }
}
