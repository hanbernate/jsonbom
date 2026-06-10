package io.github.hanbernate.jsonbom.api;

import org.reactivestreams.Publisher;

import java.util.Map;
/**
 * JSON BOM Mapper interface that provides functionality for mapping JSON Bill of Materials (BOM)
 * to Java objects.
 * <p>
 * This interface allows registration of type schemas and value handlers, and enables mapping
 * of Reactive Streams based BOM data streams into object streams of specified types.
 *
 * @author hanbernate
 * @since 0.0.1
 */
public interface JsonBomMapper {
    /**
     * Registers a schema for the specified response type if not already present,
     * and returns the registered schema instance.
     * <p>
     * This method manages type mapping metadata, ensuring that each response type
     * has only one corresponding Schema instance.
     *
     * @param <T> the generic type of the response type
     * @param responseType the Class object of the target response type
     * @return the registered or newly created Schema instance describing the structure of the type
     * @since 0.0.1
     */
    <T> Schema<T> registerSchemaIfAbsent(Class<T> responseType);

    /**
     * Registers a value handler for the specified response type.
     * <p>
     * Value handlers are used to process conversion, formatting, or other custom logic
     * for values of specific type fields.
     *
     * @param responseType the Class object of the target response type to handle
     * @param valueHandler the value handler instance to register
     * @return the previously registered value handler associated with the type, or null if none existed
     * @since 0.0.1
     */
    ValueHandler<?>registerValueHandler(Class<?> responseType, ValueHandler<?> valueHandler);

    /**
     * Maps a BOM data stream into an object stream of the specified target type.
     * <p>
     * The mapping process is assisted by the provided model data publishers.
     *
     * @param <T> the generic type of the target type
     * @param bomPublisher the publisher of BOM data source, providing a continuous stream of BOM items
     * @param targetType the Class object of the target type
     * @param models a map from model names to model data publishers, used for referencing
     *               other model data during the mapping process
     * @return a Publisher stream containing mapped objects, where each BOM item is converted
     *         into an instance of the target type
     * @since 0.0.1
     */
    <T> Publisher<T> map(Publisher<Bom> bomPublisher, Class<T> targetType, Map<String, Publisher<?>> models);

    /**
     * Maps a BOM data stream into an object stream of the specified target type,
     * with explicit specification of the source model type.
     * 
     *
     * @param <T> the generic type of the target type
     * @param <U> the generic type of the source model type
     * @param bomPublisher the publisher of BOM data source, providing a continuous stream of BOM items
     * @param targetType the Class object of the target type
     * @param modelType the Class object of the source model type, used for type-safe model references
     * @param sourceModels a map from model names to model data publishers, which will be used
     *                     according to the modelType
     * @return a Publisher stream containing mapped objects, where each BOM item is converted
     *         into an instance of the target type
     */
    <T,U> Publisher<T> map(Publisher<Bom> bomPublisher, Class<T> targetType, Class<U> modelType, Map<String, Publisher<?>> sourceModels);
    
    /**
     * Returns the BOM adapter used by this mapper.
     * <p>
     * The BOM adapter is responsible for low-level BOM data parsing and access,
     * providing a unified interface for BOM-specific data operations.
     *
     * @return the current BomAdapter instance used for handling BOM-specific data operations
     */
    BomAdapter getBomAdapter();
}
