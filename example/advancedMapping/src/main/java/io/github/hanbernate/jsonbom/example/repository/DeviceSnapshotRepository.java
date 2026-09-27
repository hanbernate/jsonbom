package io.github.hanbernate.jsonbom.example.repository;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.example.model.DeviceSnapshot;

import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Simulated device repository.
 * <p>
 * The incoming {@link Bom} is the sub-BOM selected by the orchestrator for the
 * {@code device} model; whether a field was requested can be tested with
 * {@code containsKey}, which is how on-demand loading is demonstrated in the
 * other examples.
 */
@Repository
public class DeviceSnapshotRepository {

    public Mono<DeviceSnapshot> findById(Mono<Bom> bom, Mono<String> deviceId) {
        return Mono.zip(bom, deviceId, (b, id) -> {
            boolean wantsModel = b.containsKey("model");
            boolean wantsLocation = b.containsKey("location");
            return new DeviceSnapshot(
                    id,
                    wantsModel ? "M-9" : null,
                    wantsLocation ? "Block A / Row 12" : null);
        });
    }
}
