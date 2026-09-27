package io.github.hanbernate.jsonbom.example.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Opaque device payload.
 * <p>
 * The matching field of {@link MeterReport} is annotated with
 * {@code @BomMapping(value = "raw", valueNode = true)} so the schema factory
 * stops at this type and never introspects its fields: the model object is used
 * as-is and passed straight through to the response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RawPayload {
    private String hex;
    private int length;
}
