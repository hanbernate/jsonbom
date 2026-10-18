package io.github.hanbernate.jsonbom.util;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.function.Function;

import io.github.hanbernate.jsonbom.api.BomMapping;
import io.github.hanbernate.jsonbom.api.JsonBomException;

/**
 * Utility class for resolving types and extracting values from {@link BomMapping} annotations
 * during JSON Schema generation.
 *
 * @since 0.2.0
 */
public class SchemaUtils {

    /**
     * Resolves the actual type based on the BomMapping and response type.
     * If the BomMapping has a non-Void generic type, returns that type.
     * If the response type is an array or Collection, extracts the generic type from the parent field.
     * Otherwise, returns the response type.
     *
     * @param f the field being processed
     * @param parent the parent class
     * @param bomMapping the BomMapping annotation
     * @param responseType the response type
     * @param <T> the type parameter
     * @return the resolved class type
     * @since 0.2.0
     */
    public static <T> Class<T> resolveActulaType(Field f, Class<?> parent, BomMapping bomMapping, Class<?> responseType){
        if(Void.class != getBomMappingValue(bomMapping, BomMapping::genericType, Void.class)) {
            return (Class<T>) bomMapping.genericType();
        }else if(responseType.isArray() || Collection.class.isAssignableFrom(responseType)){
            return getGenericType(parent, f);
        }
        return (Class<T>) responseType;

    }
    
    /**
     * Gets a value from the BomMapping using the provided function, or returns the default value if the mapping is null.
     *
     * @param bomMapping the BomMapping annotation, may be null
     * @param func the function to extract the value from BomMapping
     * @param defaultValue the default value to return if bomMapping is null
     * @param <T> the type of value
     * @return the extracted value or default
     * @since 0.2.0
     */
    public static <T> T getBomMappingValue(BomMapping bomMapping, Function<BomMapping, T> func, T defaultValue){
        if(null == bomMapping){
            return defaultValue;
        }
        return func.apply(bomMapping);
    }

    private static <T> Class<T> getGenericType(Class<?> parent, Field f){
        java.lang.reflect.Type genericType = f.getGenericType();
        if(genericType.getClass().isAssignableFrom(Class.class)){
            return (Class<T>) genericType;
        }

        if(genericType instanceof GenericArrayType){
            throw new JsonBomException("Generic array is not supported.");
        }

        if(genericType instanceof WildcardType){
            throw new JsonBomException("WildcardType is not supported. Plaes use @BomMapping(genericType=*) to specify generic type instead.");
        }
        if(genericType instanceof TypeVariable){
            java.lang.reflect.Type actual = getGenericType((TypeVariable<?>) genericType, parent);
            if(actual instanceof Class){
                return (Class<T>) actual;
            }
            throw new JsonBomException("Fail parsing generic type:" + actual.getTypeName());
        }
        java.lang.reflect.Type type = ((ParameterizedType) genericType).getActualTypeArguments()[0];
        if(type instanceof TypeVariable){
            java.lang.reflect.Type actual = getGenericType((TypeVariable<?>) type, parent);
            if(actual instanceof Class){
                return (Class<T>) actual;
            }
            throw new JsonBomException("Fail parsing generic type:" + type.getTypeName());
        }else if(type instanceof ParameterizedType){
            throw new JsonBomException("Nest generic type is not supported.");
        }else if(type instanceof WildcardType){
            throw new JsonBomException("WildcardType is not supported. Plaes use @BomMapping(genericType=*) to specify generic type instead.");
        }else if(type instanceof GenericArrayType){
            throw new JsonBomException("Generic array is not supported.");
        }
        throw new JsonBomException("Fail parsing generic type:" + genericType.getTypeName());
    }
    
    private static java.lang.reflect.Type getGenericType(TypeVariable<?> targTypeVariable, Class<?> cls){
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
                java.lang.reflect.Type genericSuperClass= cls.getGenericSuperclass();
                if(genericSuperClass instanceof ParameterizedType){
                    return ((ParameterizedType)cls.getGenericSuperclass()).getActualTypeArguments()[i];
                }
                throw new JsonBomException("Fail parsing generic type:" + genericSuperClass.getTypeName());
            }
        }
        return targTypeVariable;
    }
}
