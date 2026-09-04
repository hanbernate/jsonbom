package io.github.hanbernate.jsonbom.victools;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.ResolvedTypeWithMembers;
import com.fasterxml.classmate.members.ResolvedField;
import com.github.victools.jsonschema.generator.CustomPropertyDefinition;
import com.github.victools.jsonschema.generator.CustomPropertyDefinitionProvider;
import com.github.victools.jsonschema.generator.FieldScope;
import com.github.victools.jsonschema.generator.SchemaGenerationContext;

import io.github.hanbernate.jsonbom.api.BomType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public class BomPropertyDefinitionProvider  implements CustomPropertyDefinitionProvider<FieldScope>{

    @Override
    public CustomPropertyDefinition provideCustomSchemaDefinition(FieldScope fieldScope,
            SchemaGenerationContext context) {
        BomType bomType = fieldScope.getAnnotation(BomType.class);
        if(null == bomType){
            return null;
        }
        Class<?> responseType = bomType.value();
        ObjectMapper objectMapper = context.getGeneratorConfig().getObjectMapper();
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "object");
        ObjectNode properties = objectNode.putObject("properties");
        ResolvedType resolvedType = context.getTypeContext().resolve(responseType);
        ResolvedTypeWithMembers typeWithMembers = context.getTypeContext().resolveWithMembers(resolvedType);
        for(ResolvedField field : typeWithMembers.getMemberFields()){
            String name = field.getName();
            properties.set(name, createByField(context.getTypeContext().createFieldScope(field, typeWithMembers), context));
        }
        objectNode.put("description", context.getGeneratorConfig().resolveDescription(fieldScope));
        return new CustomPropertyDefinition(objectNode);
    }

    private ObjectNode createByField(FieldScope fieldScope, SchemaGenerationContext context){
        var objectMapper = context.getGeneratorConfig().getObjectMapper();
        ObjectNode r = objectMapper.createObjectNode();
        Type type = fieldScope.getRawMember().getGenericType();
        if(isNested(type)){
            ObjectNode properties = createNestedType(type, context);
            r.put("type", "object");
            r.set("properties", properties);
        }else{
            r.put("type", "string");
        }
        String description = context.getGeneratorConfig().resolveDescription(fieldScope);
        if(null != description){
            r.put("description", description);
        }
        return r;
    }

    private boolean isNested(Type type){
        
        if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;
            if (clazz.isArray()) {
                return true;
            }
            if (clazz.isPrimitive()) {
                return false;
            }
            if (Number.class.isAssignableFrom(clazz)
                    || clazz == Character.class
                    || clazz == Boolean.class
                    || clazz == String.class) {
                return false;
            }
            return true;
        }
        // GenericArrayType、ParameterizedType 等其他 Type 一律视为嵌套
        return true;
    }

    private ObjectNode createNestedType(Type type, SchemaGenerationContext context){
        ObjectNode properties = context.getGeneratorConfig().createObjectNode();
        ResolvedType resolvedType = resolveType(type, context);
        ResolvedTypeWithMembers typeWithMembers = context.getTypeContext().resolveWithMembers(resolvedType);
        for(ResolvedField field : typeWithMembers.getMemberFields()){
            String name = field.getName();
            properties.set(name, createByField(context.getTypeContext().createFieldScope(field, typeWithMembers), context));
        }
        return properties;
    }

    private ResolvedType resolveType(Type type, SchemaGenerationContext context){
        if(ParameterizedType.class.isAssignableFrom(type.getClass())){
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            if(1 == actualTypeArguments.length){
                return context.getTypeContext().resolve(actualTypeArguments[0]);
            }
        }
        return context.getTypeContext().resolve(type);
    }

}
