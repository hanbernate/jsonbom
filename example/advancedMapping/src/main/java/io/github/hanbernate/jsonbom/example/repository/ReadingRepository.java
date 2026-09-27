package io.github.hanbernate.jsonbom.example.repository;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.example.model.Reading;

import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Simulated reading repository.
 * <p>
 * {@link #findByDeviceId} returns a {@link Flux}, which can only be mapped to a
 * {@code List} or {@code Set} — never to an array. {@link #findAlerts} returns a
 * {@code Mono<Reading[]>}, the supported way to feed an array response.
 */
@Repository
public class ReadingRepository {

    public Flux<Reading> findByDeviceId(Mono<Bom> bom, Mono<String> deviceId) {
        return Flux.zip(bom, deviceId)
                .flatMap(t -> Flux.just(
                        new Reading("2026-01-01T00:00", 12.5d),
                        new Reading("2026-01-01T01:00", 12.9d),
                        new Reading("2026-01-01T02:00", 13.1d)));
    }

    public Mono<Reading[]> findAlerts(Mono<Bom> bom, Mono<String> deviceId) {
        return Mono.zip(bom, deviceId, (b, id) -> new Reading[]{
                new Reading("2026-01-01T02:00", 99.0d),
                new Reading("2026-01-01T03:00", 98.4d)
        });
    }
}
