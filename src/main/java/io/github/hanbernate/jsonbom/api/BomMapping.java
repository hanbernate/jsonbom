package io.github.hanbernate.jsonbom.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to map a JavaBean field to a specific path or key in a BOM (Bill of Materials) structure.
 * <p>
 * This annotation provides metadata for the JSON BOM mapping framework, allowing fine-grained control
 * over how fields are populated from BOM data during the mapping process.
 * <p>
 * The path separator used in the {@link #value()} is configurable at the framework level.
 * By default, the forward slash character ({@code /}) is used as the separator, but users can customize
 * it through the framework configuration (e.g., {@code .}, {@code ->}, or any other delimiter).
 * <p>
 *
 * @author hanbernate
 * @since 0.0.1
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface BomMapping {
    /**
     * Specifies the path or key in the BOM structure that maps to this field.
     * <p>
     * The value can be a simple key name or a delimited path for nested structures.
     * For example: with default separator {@code /}, the path {@code "user/name"} maps to
     * {@code {"user": {"name": value}}}.
     * <p>
     * The actual separator character is configurable at the framework level.
     *
     * @return the BOM path or key associated with this field
     * @since 0.0.1
     */
    String value();
    
    /**
     * Specifies the generic type for collection fields (e.g., {@code List<T>}, {@code Set<T>}).
     * <p>
     * This is used when the annotated field is a parameterized collection type,
     * allowing the framework to determine the actual element type for deserialization.
     * The default value {@code Void.class} indicates that no generic type is specified.
     *
     * @return the generic type class for collection elements
     * @since 0.0.1
     */ 
    Class<?> genericType() default Void.class;

    /**
     * Specifies a custom value handler class for processing the field's value.
     * <p>
     * Value handlers can be used to perform custom conversion or formatting
     * on the BOM value before it is set to the field.
     *
     * @return the value handler class to use for this field
     * @since 0.0.1
     */
    Class<? extends ValueHandler> valueHandler() default ValueHandler.class;
    
    /**
     * Indicates whether this field corresponds to a leaf value node rather than a nested BOM structure.
     * <p>
     * When set to {@code true}, the field is treated as a terminal value node.
     * When {@code false} (default), the field may contain a nested BOM structure.
     *
     * @return {@code true} if the field is a leaf value node, {@code false} otherwise
     * @since 0.0.1
     */
    boolean valueNode() default false;
}
