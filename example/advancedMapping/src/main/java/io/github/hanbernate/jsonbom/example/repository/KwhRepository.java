package io.github.hanbernate.jsonbom.example.repository;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.example.model.KwhSample;

import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Simulated energy repository. It returns a raw {@link KwhSample}; the
 * registered default value handler is responsible for turning it into a
 * {@code Kwh} response object.
 */
@Repository
public class KwhRepository {

    public Mono<KwhSample> findLatest(Mono<Bom> bom, Mono<String> deviceId) {
        return Mono.zip(bom, deviceId, (b, id) -> new KwhSample(new BigDecimal("153.456")));
    }
}
