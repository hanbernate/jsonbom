package io.github.hanbernate.jsonbom.api;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default implementation of the ValueHandlers interface.
 * <p>
 * This implementation provides:
 * <ul>
 *     <li>Thread-safe registration and caching using ConcurrentHashMap</li>
 *     <li>Lazy instantiation of value handlers via {@link BeanUtil}</li>
 *     <li>Global singleton caches shared across all instances</li>
 * </ul>
 *
 * @author hanbernate
 * @since 0.0.1
 */
public class DefaultValueHandlersImpl implements ValueHandlers {
    // Cache for type -> value handler mappings (registered by response type)
    private static final ConcurrentMap<Class<?>, ValueHandler<?>> typeCache = new ConcurrentHashMap<>();
    // Cache for value handler class in @BomMapping -> instance (lazy instantiation cache)
    private static final ConcurrentMap<Class<?>, ValueHandler<?>> bomMappingCache = new ConcurrentHashMap<>();

    private BeanUtil beanUtil;

    /**
     * Sets the BeanUtil instance used for instantiating value handler classes.
     *
     * @param beanUtil the BeanUtil instance to use
     * @return the previously configured BeanUtil
     * @since 0.0.1
     */
    public BeanUtil setBeanUtil(BeanUtil beanUtil){
        this.beanUtil = beanUtil;
        return this.beanUtil;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ValueHandler<?> register(Class<?> responseType, ValueHandler<?> valueHandler) {
        return typeCache.putIfAbsent(responseType, valueHandler);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Implementation uses {@link ConcurrentMap#computeIfAbsent} to ensure only one
     * instance is created per handler class.
     *
     * @throws JsonBomException if the class cannot be instantiated
     */
    @Override
    public ValueHandler<?> getOrCreate(Class<? extends ValueHandler<?>> valueHandlerClass) {
        return bomMappingCache.computeIfAbsent(valueHandlerClass, cls -> beanUtil.instantiateClass(valueHandlerClass));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ValueHandler<?> getByResponseType(Class<?> responseType) {
        return typeCache.get(responseType);
    }
}
