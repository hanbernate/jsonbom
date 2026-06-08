package io.github.hanbernate.jsonbom.api;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SchemaFactoryImpl implements SchemaFactory {
    ValueHandlers valueHandlers;

    Function<Field, String> nameParser = f -> f.getName();

    private ConcurrentMap<Class<?>, Map<String, Schema<?>>> childrenCache = new ConcurrentHashMap<>();

    private String seprator = "/";

    private BeanUtil beanUtil;
    
    private Map<Class<?>, Schema<?>> rootSchemas = new ConcurrentHashMap<>();

    @Override
    public Function<Field, String> setNameParser(Function<Field, String> nameParser) {
        this.nameParser = nameParser;
        return this.nameParser;
    }

    @Override
    public ValueHandlers setValueHandlers(ValueHandlers valueHandlers) {
        this.valueHandlers = valueHandlers;
        return this.valueHandlers;
    }

    @Override
    public String setSeprator(String seprator){
        this.seprator = seprator;
        return this.seprator;
    }

    public BeanUtil setBeanUtil(BeanUtil beanUtil){
        this.beanUtil = beanUtil;
        return this.beanUtil;
    }


    @Override
    public <T> Schema<T> getByType(Class<T> clazz) {
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

    private <T> Schema<T> create(Schema<?> parent, Field f){
        BomMapping bomMapping = f.getAnnotation(BomMapping.class);

        PropertyDescriptor pd = beanUtil.getPropertyDescriptor(parent.getActualType(), f.getName());
        if(null == pd || null == pd.getWriteMethod()){
            return null;
        }
        Schema<T> result = new Schema<>();
        result.setWriteMethod(pd.getWriteMethod());

        if(null != pd.getReadMethod()){
            result.setReadMeothd(pd.getReadMethod());
        }

        List<String> paths = Arrays.stream(getBomMappingValue(bomMapping, BomMapping::value, f.getName()).split(seprator))
                .filter(s -> !"".equals(s))
                .collect(Collectors.toUnmodifiableList());
        result.setPath(paths);

        result.setName(this.nameParser.apply(f));

        Class<?> responseType =  f.getType();
        result.setResponseType(responseType);
        result.setActualType((Class<T>) responseType);

        if(ValueHandler.class != getBomMappingValue(bomMapping, BomMapping::valueHandler, ValueHandler.class)){
            Class<? extends ValueHandler> vc = bomMapping.valueHandler();
            ValueHandler<T> valueHandler = (ValueHandler<T>) valueHandlers.getOrCreate(vc);
            result.setValueHandler(valueHandler);
            return result;
        }

        ValueHandler<T> valueHandler = (ValueHandler<T>) valueHandlers.getByResponseType(responseType);
        if(null != valueHandler){
            result.setValueHandler(valueHandler);
            return result;
        }

        if(Void.class != getBomMappingValue(bomMapping, BomMapping::genericType, Void.class)) {
            result.setActualType((Class<T>) bomMapping.genericType());
        }else if(result.isResponseCollection()){
            throw new NullPointerException("genericType cannot be null for " + result.getResponseType().getName());
        }

        Class<?> actualType = result.getActualType();
        if(getBomMappingValue(bomMapping, BomMapping::valueNode, false) || actualType.isPrimitive() || actualType.getPackageName().startsWith("java") || Enum.class.isAssignableFrom(actualType)){
            return result;
        }
        result.setChildren(getOrCreateChildren(actualType, result));
        return result;
    }

    private <T> T getBomMappingValue(BomMapping bomMapping, Function<BomMapping, T> func, T defaultValue){
        if(null == bomMapping){
            return defaultValue;
        }
        return func.apply(bomMapping);
    }

    private Map<String, Schema<?>> getOrCreateChildren(Class<?> actualType, Schema<?> parent){

        Map<String, Schema<?>> children = childrenCache.getOrDefault(actualType, new ConcurrentHashMap<>());
        if(children.size() > 0){
            return children;
        }
        Map<String, Schema<?>> exists = childrenCache.putIfAbsent(actualType, children);
        if(null != exists){
            return exists;
        }
        children = null == exists ? children : exists;
        for(Field childField : actualType.getDeclaredFields()){
            Schema<?> child = create(parent, childField);
            if(null != child) {
                children.putIfAbsent(child.getName(), child);
            }
        }
        return children;
    }
}
