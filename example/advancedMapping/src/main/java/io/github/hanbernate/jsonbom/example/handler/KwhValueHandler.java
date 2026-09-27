package io.github.hanbernate.jsonbom.example.handler;

import io.github.hanbernate.jsonbom.api.ValueHandler;
import io.github.hanbernate.jsonbom.example.model.Kwh;
import io.github.hanbernate.jsonbom.example.model.KwhSample;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Default value handler for the {@link Kwh} return type.
 * <p>
 * It is registered once, by type, on the mapper
 * ({@code mapper.registerValueHandler(Kwh.class, new KwhValueHandler())}),
 * so every field whose declared type is {@code Kwh} is handled automatically —
 * no {@code @BomMapping.valueHandler} is required on the fields themselves.
 * <p>
 * The handler normalizes the raw sample pulled from the repository and labels
 * the unit.
 */
public class KwhValueHandler implements ValueHandler<Kwh> {

    @Override
    public Kwh apply(Object model, String bomValue) {
        if (model instanceof KwhSample sample && sample.getRawValue() != null) {
            return new Kwh(sample.getRawValue().setScale(2, RoundingMode.HALF_UP), "kWh");
        }
        if (model instanceof Kwh kwh) {
            // Already resolved (e.g. re-applied during heterogeneous transformation).
            return kwh;
        }
        if (bomValue != null && !bomValue.isBlank()) {
            return new Kwh(new BigDecimal(bomValue).setScale(2, RoundingMode.HALF_UP), "kWh");
        }
        return null;
    }
}
