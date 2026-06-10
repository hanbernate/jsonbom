package io.github.hanbernate.jsonbom.api;

import java.beans.PropertyDescriptor;

/**
 * Bean utility interface providing basic operations for JavaBean property descriptor
 * retrieval and class instantiation.
 * <p>
 * This interface encapsulates core JavaBean introspection operations for dynamically
 * manipulating Java objects during the JSON BOM mapping process, including obtaining
 * property metadata and creating class instances.
 * <p>
 * This API has been established since version 0.0.1.
 *
 * @author hanbernate
 * @since 0.0.1
 */
public interface BeanUtil {
     /**
     * Returns the property descriptor for the specified property of the given class.
     * <p>
     * The property descriptor contains metadata about the property, including its type,
     * read method (getter), and write method (setter).
     *
     * @param clazz the Class object of the target class
     * @param propertyName the property name (following JavaBean naming conventions)
     * @return the PropertyDescriptor instance for the property, containing complete property metadata
     * @throws JsonBomException if the property does not exist, an introspection error occurs,
     *                          or any other exceptional condition arises
     * @since 0.0.1
     */
    PropertyDescriptor getPropertyDescriptor(Class<?> clazz, String propertyName) throws JsonBomException;
 /**
     * Instantiates an object of the specified class.
     * <p>
     * Typically creates an instance by invoking the class's default no-argument constructor.
     * If instantiation fails, a runtime exception is thrown.
     *
     * @param <T> the generic type of the target class
     * @param clazz the Class object of the class to instantiate
     * @return a new instance of the class
     * @throws JsonBomException if the class cannot be instantiated (e.g., no no-argument constructor,
     *                          the constructor is inaccessible, the constructor throws an exception,
     *                          the class is abstract or an interface, etc.)
     * @since 0.0.1
     */
    <T> T instantiateClass(Class<T> clazz) throws JsonBomException;
}
