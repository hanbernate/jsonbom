package io.github.hanbernate.jsonbom.api;

/**
 * Handler interface for processing and converting BOM string values into target Java objects.
 * <p>
 * Value handlers are used to perform custom conversion or formatting on leaf values
 * extracted from a BOM structure before they are set to a target field.
 * <p>
 * Typical use cases include:
 * <ul>
 *     <li>Converting string values to custom types (e.g., date parsing, number formatting)</li>
 *     <li>Transforming or sanitizing string content</li>
 *     <li>Applying business-specific conversion logic</li>
 * </ul>
 * <p>
 * Unlike validation logic, value handlers focus solely on transformation and conversion.
 *
 * @param <R> the return type after processing the BOM value
 * @author hanbernate
 * @since 0.0.1
 */
public interface ValueHandler<R> {
   /**
     * Applies the value handler logic to convert the given BOM string value
     * into the target type.
     * <p>
     * The {@code model} parameter provides access to the parent object being built,
     * allowing context-aware transformations if needed.
     *
     * @param model the parent model object being populated (may be {@code null}
     *              for root-level fields)
     * @param bomValue the raw string value from the BOM structure
     * @return the converted value to be set on the target field
     * @since 0.0.1
     */
   R apply(Object model, String bomValue);
}
