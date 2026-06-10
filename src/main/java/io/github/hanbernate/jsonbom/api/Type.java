package io.github.hanbernate.jsonbom.api;

/**
 * Represents the type of a {@link BomOrValue} node in a JSON BOM structure.
 * <p>
 * This enum distinguishes between two possible states of a BOM node:
 * <ul>
 *     <li>{@link #VALUE} - A leaf node containing a string value</li>
 *     <li>{@link #BOM} - A container node containing a nested BOM structure</li>
 * </ul>
 *
 * @author hanbernate
 * @since 0.0.1
 */
public enum Type {

    /**
     * Indicates that the node is a leaf value node.
     * <p>
     * When a {@link BomOrValue} has this type, it contains a non-null {@code value}
     * and a {@code null} {@code bom} reference.
     */
    VALUE,

    /**
     * Indicates that the node is a container node with a nested BOM structure.
     * <p>
     * When a {@link BomOrValue} has this type, it contains a non-null {@code bom}
     * and a {@code null} {@code value} reference.
     */
    BOM;
}
