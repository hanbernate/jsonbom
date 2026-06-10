package io.github.hanbernate.jsonbom.spring;

import io.github.hanbernate.jsonbom.api.BeanUtil;
import io.github.hanbernate.jsonbom.api.JsonBomException;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;

import java.beans.PropertyDescriptor;

public class SpringBeanUtil implements BeanUtil {
    /**
     * {@inheritDoc}
     *
     * @since 0.0.1
     */
    @Override
    public PropertyDescriptor getPropertyDescriptor(Class<?> clazz, String propertyName) throws JsonBomException {
        try{
            return BeanUtils.getPropertyDescriptor(clazz, propertyName);
        }catch(BeansException e){
            throw new JsonBomException("Fail to get property descriptor.", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.0.1
     */
    @Override
    public <T> T instantiateClass(Class<T> clazz) throws JsonBomException{
        try{
            return BeanUtils.instantiateClass(clazz);
        }catch(Exception e){
            throw new JsonBomException("Fail to instantiate new instance.", e);
        }
    }
}
