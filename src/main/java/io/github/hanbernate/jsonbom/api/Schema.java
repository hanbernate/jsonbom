package io.github.hanbernate.jsonbom.api;


import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents the schema metadata for a Java type used in JSON BOM mapping.
 * <p>
 * A Schema contains information about a type's structure, including its fields
 * (children), property accessor methods (getter/setter), value handlers, and
 * path information for nested mappings.
 * <p>
 * This class is used internally by the mapping framework to understand how to
 * transform BOM data into Java objects and vice versa.
 *
 * @param <R> the actual Java type this schema represents
 * @author hanbernate
 * @since 0.0.1
 */
public class Schema<R>{
    List<String> path = Collections.emptyList();
    String name;
    Class<R> actualType;
    Method writeMethod;
    Method readMethod;

    Class<?> responseType;
    ValueHandler<R> valueHandler;
    Map<String, Schema<?>> children = Collections.emptyMap();

    Schema(){}

    /**
     * Returns the path of this schema within the overall object structure.
     * <p>
     * The path is a list of field names from the root to this schema's position.
     *
     * @return an immutable list of path segments
     * @since 0.0.1
     */
    public List<String> getPath() {
        return path;
    }

    void setPath(List<String> path) {
        this.path = path;
    }

    /**
     * Returns the write method (setter) for this schema's property.
     *
     * @return the Method object representing the setter, or {@code null} if not available
     * @since 0.0.1
     */
    public Method getWriteMethod() {
        return writeMethod;
    }

    void setWriteMethod(Method writeMethod) {
        this.writeMethod = writeMethod;
    }

    /**
     * Returns the read method (getter) for this schema's property.
     *
     * @return the Method object representing the getter, or {@code null} if not available
     * @since 0.0.1
     */
    public Method getReadMethod() {
        return readMethod;
    }

    public void setReadMethod(Method readMethod) {
        this.readMethod = readMethod;
    }

    /**
     * Returns the actual Java type this schema represents.
     *
     * @return the Class object of the actual type
     * @since 0.0.1
     */
    public Class<R> getActualType() {
        return actualType;
    }

    public void setActualType(Class<R> actualType) {
        this.actualType = actualType;
    }

    /**
     * Returns the name of this schema property.
     *
     * @return the property name
     * @since 0.0.1
     */
    public String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the value handler associated with this schema.
     * <p>
     * Value handlers perform custom conversion or formatting on values
     * before they are set to the target field.
     *
     * @return the ValueHandler instance, or {@code null} if none is associated
     * @since 0.0.1
     */
    public ValueHandler<R> getValueHandler() {
        return valueHandler;
    }

    void setValueHandler(ValueHandler<R> valueHandler) {
        this.valueHandler = valueHandler;
    }

    

    /**
     * Returns the child schemas for nested properties of this schema.
     * <p>
     * The map key is the property name, and the value is the child Schema
     * describing that property's structure.
     *
     * @return a map of property names to child schemas which will never be null
     * @since 0.0.1
     */
    public Map<String, Schema<?>> getChildren() {
        return children;
    }

    void setChildren(Map<String, Schema<?>> children) {
        this.children = children;
    }

    /**
     * Returns the response type associated with this schema.
     * <p>
     * For root schemas, this is the target type being mapped.
     * For nested schemas, this may be the declaring type of the property.
     *
     * @return the response type Class
     * @since 0.0.1
     */
    public Class<?> getResponseType() {
        return responseType;
    }

    void setResponseType(Class<?> responseType) {
        this.responseType = responseType;
    }

    /**
     * Determines whether the response type is a collection or array.
     * <p>
     * This method checks if the response type is an array or implements
     * the {@link Collection} interface.
     *
     * @return {@code true} if the response type is a collection or array,
     *         {@code false} otherwise
     * @since 0.0.1
     */
    public boolean isResponseCollection(){
        return responseType.isArray() || Collection.class.isAssignableFrom(responseType);
    }

    /**
     * Returns a string representation of this schema for exception messages.
     * <p>
     * The output includes the schema name and its path joined by the specified
     * path separator.
     *
     * @param pathSeparator the separator to use when joining path segments
     * @return a formatted string containing the name and path
     * @since 0.0.1
     */
    public String toString4Exception(String pathSeprator){
        return "name="+ this.getName() +
            ", path=" + this.getPath().stream().collect(Collectors.joining(pathSeprator));
    }
}
