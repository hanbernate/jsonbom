package io.github.hanbernate.jsonbom.api;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.introspect.POJOPropertiesCollector;


/**
 * Default implementation of the SchemaFactory interface.
 * <p>
 * This implementation provides:
 * <ul>
 *     <li>Thread-safe caching of schemas using ConcurrentHashMap</li>
 *     <li>Automatic schema generation from JavaBean fields and annotations</li>
 *     <li>Support for custom name parsers and value handlers</li>
 *     <li>Configurable path separator for BOM field mappings (default is "/")</li>
 * </ul>
 *
 * @author hanbernate
 * @since 0.0.1
 */
public class DefaultSchemaFactoryImpl implements SchemaFactory {
    
    // Registry for value handlers by type
    private ValueHandlers valueHandlers;

    // Function to extract schema name from a Field. Defaults to field name.
    private Function<Field, String> nameParser = f -> f.getName();

    // Cache for child schemas per class type to avoid repeated introspection
    private ConcurrentMap<Class<?>, Map<String, Schema<?>>> childrenCache = new ConcurrentHashMap<>();

    /**
     * The path separator used for splitting BOM field paths.
     * Default value is "/" (forward slash).
     */
    private String separator = "/";

    private BeanUtil beanUtil;

    // Cache for root-level schemas (top-level types)
    private Map<Class<?>, Schema<?>> rootSchemas = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public Function<Field, String> setNameParser(Function<Field, String> nameParser) {
        this.nameParser = nameParser;
        return this.nameParser;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ValueHandlers setValueHandlers(ValueHandlers valueHandlers) {
        this.valueHandlers = valueHandlers;
        return this.valueHandlers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String setSeparator(String separator){
        this.separator = separator;
        return this.separator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSeparator(){
        return this.separator;
    }

    /**
     * Sets the BeanUtil instance used for JavaBean introspection.
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
     * <p>
     * Implementation details:
     * <ul>
     *     <li>Checks the root schema cache first</li>
     *     <li>If not cached, creates a new schema by analyzing the class structure</li>
     *     <li>Thread-safely stores the created schema in the cache</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> Schema<T> getByType(Class<T> clazz) {
        // Fast path: return cached schema if available
        Schema<T> result = (Schema<T>) rootSchemas.get(clazz);
        if(null != result){
            return result;
        }

        result = new Schema<>();
        result.setResponseType(clazz);
        result.setActualType(clazz);

        result.setChildren(getOrCreateChildren(clazz, result));

        Schema<T> exists = (Schema<T>) rootSchemas.putIfAbsent(clazz, result);
        return null != exists ? exists : result;
    }

    @SuppressWarnings("unchecked")
    private <T> Schema<T> create(Schema<?> parent, Field f){
        BomMapping bomMapping = f.getAnnotation(BomMapping.class);

        PropertyDescriptor pd = beanUtil.getPropertyDescriptor(parent.getActualType(), f.getName());
        if(null == pd || null == pd.getWriteMethod()){
            return null;
        }
        Schema<T> result = new Schema<>();
        result.setWriteMethod(pd.getWriteMethod());

        if(null != pd.getReadMethod()){
            result.setReadMethod(pd.getReadMethod());
        }

        // Parse BOM path: use annotation value if present, otherwise use field name as default
        List<String> paths = Arrays.stream(getBomMappingValue(bomMapping, BomMapping::value, f.getName()).split(Pattern.quote(separator)))
                .filter(s -> !"".equals(s))
                .collect(Collectors.toUnmodifiableList());
        result.setPath(paths);

        result.setName(this.nameParser.apply(f));

        Class<?> responseType =  f.getType();
        result.setResponseType(responseType);
        result.setActualType((Class<T>) responseType);

        // Priority 1: Custom value handler specified in @BomMapping
        if(ValueHandler.class != getBomMappingValue(bomMapping, BomMapping::valueHandler, ValueHandler.class)){
            Class<? extends ValueHandler<?>> vc = (Class<? extends ValueHandler<?>>) bomMapping.valueHandler();
            ValueHandler<T> valueHandler = (ValueHandler<T>) valueHandlers.getOrCreate(vc);
            result.setValueHandler(valueHandler);
            return result;
        }

        // Priority 2: Value handler registered by response type
        ValueHandler<T> valueHandler = (ValueHandler<T>) valueHandlers.getByResponseType(responseType);
        if(null != valueHandler){
            result.setValueHandler(valueHandler);
            return result;
        }

        // Priority 3: Handle generic type for collection fields
        if(Void.class != getBomMappingValue(bomMapping, BomMapping::genericType, Void.class)) {
            result.setActualType((Class<T>) bomMapping.genericType());
        }else if(result.isResponseCollection()){
            result.setActualType((Class<T>) getGenericType(parent, f));
        }

        // Determine if this field should be treated as a leaf (value node)
        Class<?> actualType = result.getActualType();
        if(getBomMappingValue(bomMapping, BomMapping::valueNode, false) || actualType.isPrimitive() || actualType.getPackageName().startsWith("java") || Enum.class.isAssignableFrom(actualType)){
            return result;
        }

        // For complex nested types, recursively build child schemas
        result.setChildren(getOrCreateChildren(actualType, result));
        return result;
    }

    private Class<?> getGenericType(Schema<?> parent, Field f){
        java.lang.reflect.Type type = ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0];
        if(type instanceof TypeVariable){
            java.lang.reflect.Type actual = getGenericType((TypeVariable<?>) type, parent.getActualType());
            return (Class<?>) actual;
        }
        return (Class<?>) type;
    }

    private java.lang.reflect.Type getGenericType(TypeVariable<?> targTypeVariable, Class<?> cls){
        Class<?> superClass = cls.getSuperclass();
        if(null == superClass){
            return targTypeVariable;
        }
        java.lang.reflect.Type typeVariable = getGenericType(targTypeVariable, superClass);
        if(typeVariable instanceof Class<?>){
            return typeVariable;
        }
        TypeVariable<?>[] superTypeVariables = superClass.getTypeParameters();
        for(int i = 0; i < superTypeVariables.length; i++){
            if(typeVariable == superTypeVariables[i]){
                return ((ParameterizedType)cls.getGenericSuperclass()).getActualTypeArguments()[i];
            }
        }
        return targTypeVariable;
    }

    private <T> T getBomMappingValue(BomMapping bomMapping, Function<BomMapping, T> func, T defaultValue){
        if(null == bomMapping){
            return defaultValue;
        }
        return func.apply(bomMapping);
    }

    private Map<String, Schema<?>> getOrCreateChildren(Class<?> actualType, Schema<?> parent){

        Map<String, Schema<?>> children = childrenCache.getOrDefault(actualType, new ConcurrentHashMap<>());
        if(!children.isEmpty()){
            return children;
        }
        Map<String, Schema<?>> exists = childrenCache.putIfAbsent(actualType, children);
        if(null != exists){
            return exists;
        }
        for(Class<?> cur = actualType; cur != null; cur = cur.getSuperclass()){
            for(Field childField : cur.getDeclaredFields()){
                Schema<?> child = create(parent, childField);
                if(null != child) {
                    children.putIfAbsent(child.getName(), child);
                }
            }
        }
        return children;
    }
}
