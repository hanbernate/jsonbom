package io.github.hanbernate.jsonbom.api;

import java.beans.PropertyDescriptor;

public interface BeanUtil {
    PropertyDescriptor getPropertyDescriptor(Class<?> clazz, String propertyName) throws RuntimeException;

    <T> T instantiateClass(Class<T> clazz) throws RuntimeException;
}
