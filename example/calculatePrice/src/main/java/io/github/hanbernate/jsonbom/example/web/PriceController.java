package io.github.hanbernate.jsonbom.example.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.example.PriceOrchestrator;
import io.github.hanbernate.jsonbom.example.PriceOrchestrator.PriceModel;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Inbound HTTP entry point. The client posts a compact JSON BOM as the request
 * body and receives only the fields it asked for.
 *
 * <pre>{@code
 * POST /price/42
 * Content-Type: text/plain
 *
 * {"goods":{"originalPrice":""},"discount":"","finalPrice":""}
 * }</pre>
 *
 * <p>
 * The body is parsed with the injected Jackson 3 {@code JsonMapper} bean (wired in
 * {@code AppConfig}, which registers the {@code Bom} deserializer); the response
 * {@link PriceModel} is serialized by the Spring Boot WebFlux codecs (Jackson 2).
 */
@RestController
public class PriceController {

    private final PriceOrchestrator priceOrchestrator;
    private final JsonMapper jsonMapper;

    public PriceController(PriceOrchestrator priceOrchestrator, JsonMapper jsonMapper) {
        this.priceOrchestrator = priceOrchestrator;
        this.jsonMapper = jsonMapper;
    }

    @PostMapping(value = "/price/{goodsId}",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<PriceModel> price(@PathVariable Long goodsId,
            @RequestBody(required = false) String body) {
        Bom bom = (null == body || body.isBlank()) ? new Bom() : jsonMapper.readValue(body, Bom.class);
        return priceOrchestrator.getPriceModel(Mono.just(bom), Mono.just(goodsId));
    }
}
