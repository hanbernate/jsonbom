package io.github.hanbernate.jsonbom.api;

import java.util.Map;

import org.reactivestreams.Publisher;

/**
 * Represents a model composed of reactive {@link Publisher} entries.
 * <p>
 * Implementations of this interface provide a map where each key is a model name
 * and each value is a reactive stream publisher, enabling reactive data model
 * composition for JSON BOM processing.
 *
 * @author hanbernate
 * @since 0.0.3
 */
public interface BomModel {
    /**
     * Returns the model entries as a map of model names to reactive publishers.
     *
     * @return a map where keys are model names and values are reactive {@link Publisher} instances;
     *         never {@code null}
     * @since 0.0.3
     */
    public Map<String, Publisher<?>> getModels();
}
