package io.github.hanbernate.jsonbom.example.repository;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.example.model.RawPayload;

import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Simulated payload repository. The returned object is exposed verbatim through
 * the {@code valueNode} field of the response model.
 */
@Repository
public class RawPayloadRepository {

    public Mono<RawPayload> load(Mono<Bom> bom, Mono<String> deviceId) {
        return Mono.zip(bom, deviceId, (b, id) -> new RawPayload("0xA1B2C3", 3));
    }
}
