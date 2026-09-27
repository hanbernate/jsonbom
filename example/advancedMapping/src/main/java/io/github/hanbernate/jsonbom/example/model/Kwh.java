package io.github.hanbernate.jsonbom.example.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Energy consumption value exposed in the response.
 * <p>
 * This type is <em>not</em> annotated with {@code @BomMapping.valueHandler}.
 * Instead a default handler is registered for the type itself (see
 * {@code AppConfig}), which demonstrates
 * "register a default ValueHandler for a specific return type".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Kwh {
    private BigDecimal value;
    private String unit;
}
