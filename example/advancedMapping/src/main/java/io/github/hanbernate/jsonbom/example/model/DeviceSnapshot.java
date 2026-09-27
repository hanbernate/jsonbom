package io.github.hanbernate.jsonbom.example.model;

import io.github.hanbernate.jsonbom.api.BomMapping;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Snapshot of a device, served by the device repository.
 * <p>
 * It is the <em>source model</em> for the {@code deviceId} / {@code deviceModel}
 * fields of {@link MeterReport} (both fields share the same model under the
 * {@code device} path root).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSnapshot {

    @BomMapping("deviceId")
    private String deviceId;

    @BomMapping("model")
    private String model;

    @BomMapping("location")
    private String location;
}
