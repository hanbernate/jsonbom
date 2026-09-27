package io.github.hanbernate.jsonbom.example.model;

import io.github.hanbernate.jsonbom.api.BomMapping;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * Response model of the meter-reading scenario.
 * <p>
 * It is the main showcase of the module and simultaneously plays the role of
 * "target type" in the heterogeneous transformation example. Every attribute
 * below exercises a different advanced mapping feature:
 * <ul>
 *     <li>{@link #deviceId} / {@link #deviceModel} — several fields sharing one
 *         nested path root ({@code device}) and therefore one source model.</li>
 *     <li>{@link #readings} — {@code List} response fed by a {@code Flux},
 *         element type resolved through {@code genericType}.</li>
 *     <li>{@link #readingSet} — {@code Set} response fed by the same
 *         {@code Flux}; a single reactive source can serve several
 *         collection-typed fields.</li>
 *     <li>{@link #alerts} — array response whose element type comes from
 *         {@code genericType}.</li>
 *     <li>{@link #kwh} — the {@code Kwh} type has no handler on the annotation;
 *         a default handler is registered for the type itself.</li>
 *     <li>{@link #raw} — a {@code valueNode} leaf, so the model object is used
 *         as-is without introspecting its fields.</li>
 * </ul>
 */
@Data
public class MeterReport {

    @BomMapping("device/deviceId")
    private String deviceId;

    @BomMapping("device/model")
    private String deviceModel;

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
