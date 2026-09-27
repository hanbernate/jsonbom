package io.github.hanbernate.jsonbom.example.model;

import io.github.hanbernate.jsonbom.api.BomMapping;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * Source model used by the heterogeneous transformation example.
 * <p>
 * It is <em>not</em> the response type. It describes how several independent
 * data sources (device snapshot, reading stream, energy sample, raw payload)
 * are assembled before the final {@link MeterReport} is produced.
 * <p>
 * Contract enforced by the mapper:
 * <ul>
 *     <li>the child <em>name</em> ({@code device}, {@code readings},
 *         {@code readingSet}, {@code alerts}, {@code kwh}, {@code raw}) must
 *         match the {@code path[0]} of the corresponding {@link MeterReport}
 *         field;</li>
 *     <li>each child's {@code path[0]} selects the entry of the source-models
 *         map that supplies the value.</li>
 * </ul>
 */
@Data
public class MeterSnapshot {

    @BomMapping(value = "device", genericType = DeviceSnapshot.class)
    private DeviceSnapshot device;

    @BomMapping(value = "readings", genericType = Reading.class)
    private List<Reading> readings;

    @BomMapping(value = "readingSet", genericType = Reading.class)
    private Set<Reading> readingSet;

    @BomMapping(value = "alerts", genericType = Reading.class)
    private Reading[] alerts;

    @BomMapping("kwh")
    private Kwh kwh;

    @BomMapping(value = "raw", valueNode = true)
    private RawPayload raw;
}
