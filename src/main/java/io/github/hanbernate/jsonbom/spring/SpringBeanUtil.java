package io.github.hanbernate.jsonbom.spring;

import io.github.hanbernate.jsonbom.api.BeanUtil;
import org.springframework.beans.BeanUtils;

import java.beans.PropertyDescriptor;

public class SpringBeanUtil implements BeanUtil {
    @Override
    public PropertyDescriptor getPropertyDescriptor(Class<?> clazz, String propertyName) throws RuntimeException {
        return BeanUtils.getPropertyDescriptor(clazz, propertyName);
    }

    @Override
    public <T> T instantiateClass(Class<T> clazz) throws RuntimeException{
        return BeanUtils.instantiateClass(clazz);
    }
}
