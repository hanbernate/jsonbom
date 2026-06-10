package io.github.hanbernate.jsonbom.api;

import java.lang.reflect.Field;
import java.util.function.Function;
/**
 * Factory interface for creating and managing type schemas used in JSON BOM mapping.
 *
 * @author hanbernate
 * @since 0.0.1
 */
public interface SchemaFactory {
    /**
     * Sets the path separator for BOM field paths.
     *
     * @param separator the path separator to use (e.g., "/", ".", "->")
     * @return the previously configured separator
     * @since 0.0.1
     */
    String setSeparator(String separator);

    /**
     * Returns the current path separator used for BOM field paths.
     * <p>
     * The default separator is "/" if not explicitly set.
     *
     * @return the current path separator
     * @since 0.0.1
     */
    String getSeparator();

    /**
     * Retrieves or creates a schema for the specified Java type.
     * <p>
     * The schema is cached for subsequent lookups.
     *
     * @param <T> the generic type of the target class
     * @param clazz the Class object of the target type
     * @return the schema instance for the specified type
     * @since 0.0.1
     */
    <T> Schema<T> getByType(Class<T> clazz);

     /**
     * Sets a custom name parser for converting Field objects to schema names.
     *
     * @param nameParser a function that takes a Field and returns its schema name
     * @return the previously configured name parser
     * @since 0.0.1
     */
    Function<Field, String> setNameParser(Function<Field, String> nameParser);

     /**
     * Sets the value handlers registry for this factory.
     *
     * @param valueHandlers the ValueHandlers instance to use
     * @return the previously configured ValueHandlers
     * @since 0.0.1
     */
    ValueHandlers setValueHandlers(ValueHandlers valueHandlers);
}
