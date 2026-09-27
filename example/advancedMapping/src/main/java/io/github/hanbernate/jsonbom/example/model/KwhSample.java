package io.github.hanbernate.jsonbom.example.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Raw energy sample returned by {@code KwhRepository}.
 * <p>
 * It is converted into a {@link Kwh} by
 * {@link io.github.hanbernate.jsonbom.example.handler.KwhValueHandler}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KwhSample {
    private BigDecimal rawValue;
}
