package io.github.hanbernate.jsonbom.spring;

import io.github.hanbernate.jsonbom.api.BeanUtil;
import io.github.hanbernate.jsonbom.api.JsonBomException;

import org.reactivestreams.Publisher;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spring-based implementation of {@link BeanUtil}.
 * <p>
 * Delegates bean property introspection and instantiation to Spring's
 * {@link BeanUtils}, and provides utility methods for converting Java objects
 * containing reactive {@link Publisher} properties into a map of model entries.
 *
 * @author hanbernate
 * @since 0.0.1
 */
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

    /**
     * Converts the given object into a map of model names to reactive publishers.
     * <p>
     * Only properties whose type implements {@link Publisher} are included.
     *
     * @param models the source object whose {@link Publisher} properties are to be extracted;
     *               must not be {@code null}
     * @return a map where keys are property names and values are the corresponding
     *         {@link Publisher} instances; never {@code null}
     * @since 0.0.3
     */
    public Map<String, Publisher<?>> obj2Map(Object models){
        return computePropertyDescriptors(models.getClass())
            .stream()
            .map(pd -> {
                try {
                    return new AbstractMap.SimpleImmutableEntry<String, Publisher<?>>(pd.getName(), (Publisher<?>) pd.getReadMethod().invoke(models));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new JsonBomException("Fail read property:" + pd.getName(), e);
                }
            }).filter(i -> null != i.getValue())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        

    }

    private List<PropertyDescriptor> computePropertyDescriptors(Class<?> cls){
        return Arrays.stream(BeanUtils.getPropertyDescriptors(cls))
                .filter(pd -> Publisher.class.isAssignableFrom(pd.getPropertyType()))
                .collect(Collectors.toList());
    }
}
