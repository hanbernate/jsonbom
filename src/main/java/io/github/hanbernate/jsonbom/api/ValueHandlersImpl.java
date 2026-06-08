package io.github.hanbernate.jsonbom.api;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ValueHandlersImpl implements ValueHandlers {
    protected static final ConcurrentMap<Class<?>, ValueHandler> typeMapping = new ConcurrentHashMap<>();
    protected static final ConcurrentMap<Class<?>, ValueHandler> cache = new ConcurrentHashMap<>();

    private BeanUtil beanUtil;

    public BeanUtil setBeanUtil(BeanUtil beanUtil){
        this.beanUtil = beanUtil;
        return this.beanUtil;
    }

    @Override
    public ValueHandler registry(Class<?> responseType, ValueHandler<?> valueHandler) {
        return typeMapping.putIfAbsent(responseType, valueHandler);
    }

    @Override
    public ValueHandler<?> getOrCreate(Class<? extends ValueHandler> valueHandlerClass) {
        ValueHandler valueHandler = cache.getOrDefault(valueHandlerClass, beanUtil.instantiateClass(valueHandlerClass));
        ValueHandler exists = cache.putIfAbsent(valueHandlerClass, valueHandler);
        return null != exists ? exists : valueHandler;
    }

    @Override
    public ValueHandler<?> getByResponseType(Class<?> responseType) {
        return typeMapping.get(responseType);
    }
}
