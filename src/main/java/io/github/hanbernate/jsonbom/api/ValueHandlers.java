package io.github.hanbernate.jsonbom.api;

/**
 * Registry interface for managing value handlers used in JSON BOM mapping.
 * <p>
 * Value handlers are responsible for converting raw BOM string values into
 * Java objects of specific types. This interface provides:
 * <ul>
 *     <li>Registration of handlers by response type</li>
 *     <li>Lazy instantiation of handler classes</li>
 *     <li>Lookup of handlers by response type</li>
 * </ul>
 *
 * @author hanbernate
 * @since 0.0.1
 */
public interface ValueHandlers {

    /**
     * Registers a value handler for the specified response type.
     *
     * @param type the response type that the handler can process
     * @param valueHandler the value handler instance to register
     * @return the previously registered handler for this type, or {@code null} if none existed
     * @since 0.0.1
     */
    ValueHandler<?> register(Class<?> type, ValueHandler<?> valueHandler);

    /**
     * Retrieves an existing value handler instance, or creates one using the default constructor.
     * <p>
     * The created instance is cached for subsequent lookups.
     *
     * @param valueHandlerClass the class of the value handler to retrieve or create
     * @return an instance of the specified value handler class
     * @throws JsonBomException if the class cannot be instantiated
     * @since 0.0.1
     */
    ValueHandler<?> getOrCreate(Class<? extends ValueHandler<?>> valueHandlerClass);

    /**
     * Retrieves the value handler registered for the specified response type.
     *
     * @param responseType the response type to look up
     * @return the registered value handler, or {@code null} if none is registered
     * @since 0.0.1
     */
    ValueHandler<?> getByResponseType(Class<?> responseType);
}
