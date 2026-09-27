package io.github.hanbernate.jsonbom.example.model;

import io.github.hanbernate.jsonbom.api.BomMapping;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One meter reading.
 * <p>
 * Used as the element type of {@code List}, {@code Set} and {@code array}
 * response fields, resolved through {@code @BomMapping.genericType}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reading {

    @BomMapping("readAt")
    private String readAt;

    @BomMapping("value")
    private double value;
}
