package io.github.hanbernate.jsonbom.victools;

import java.lang.reflect.Field;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.ResolvedTypeWithMembers;
import com.fasterxml.classmate.members.ResolvedField;
import com.github.victools.jsonschema.generator.CustomPropertyDefinition;
import com.github.victools.jsonschema.generator.CustomPropertyDefinitionProvider;
import com.github.victools.jsonschema.generator.FieldScope;
import com.github.victools.jsonschema.generator.SchemaGenerationContext;

import io.github.hanbernate.jsonbom.api.BomMapping;
import io.github.hanbernate.jsonbom.api.BomType;
import io.github.hanbernate.jsonbom.api.ValueHandler;
import io.github.hanbernate.jsonbom.util.SchemaUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Custom property definition provider for handling {@link BomType} annotated fields.
 * Generates JSON Schema definitions for BOM-typed properties by recursively processing
 * the target type's fields and applying {@link BomMapping} annotations.
 *
 * @since 0.2.0
 */
public class BomPropertyDefinitionProvider  implements CustomPropertyDefinitionProvider<FieldScope>{

    /**
     * {@inheritDoc}
     *
     * @since 0.2.0
     */
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
            ObjectNode value = createByField(context.getTypeContext().createFieldScope(field, typeWithMembers), responseType, context);
            properties.set(field.getName(), value);
        }
        objectNode.put("description", context.getGeneratorConfig().resolveDescription(fieldScope));
        return new CustomPropertyDefinition(objectNode);
    }

    private ObjectNode createByField(FieldScope fieldScope, Class<?> parent, SchemaGenerationContext context){
        var objectMapper = context.getGeneratorConfig().getObjectMapper();
        ObjectNode r = objectMapper.createObjectNode();
        
        String description = context.getGeneratorConfig().resolveDescription(fieldScope);
        if(null != description){
            r.put("description", description);
        }
        Field f = fieldScope.getRawMember();
        BomMapping bomMapping = f.getAnnotation(BomMapping.class);
        if(SchemaUtils.getBomMappingValue(bomMapping, BomMapping::valueNode, false)){
            return fillValueNode(r);
        }
        if(ValueHandler.class != SchemaUtils.getBomMappingValue(bomMapping, BomMapping::valueHandler, ValueHandler.class)){
            return fillValueNode(r);
        }
        //TODO: If valueHandler registered, it should be regarder as value node;

        
        Class<?> actual = SchemaUtils.resolveActulaType(f, parent, bomMapping, f.getType());
        if(actual.isPrimitive() || actual.getPackageName().startsWith("java") || Enum.class.isAssignableFrom(actual)){
            return  fillValueNode(r);
        }
        ObjectNode properties = createNestedType(actual, parent, context);
        r.put("type", "object");
        r.set("properties", properties);
        return r;
    }
    
    private ObjectNode fillValueNode(ObjectNode r){
        r.put("type", "string");
        return r;
    }
    
    private ObjectNode createNestedType(Class<?> actualType, Class<?> parent, SchemaGenerationContext context){
        ObjectNode properties = context.getGeneratorConfig().createObjectNode();
        ResolvedType resolvedType = context.getTypeContext().resolve(actualType);
        ResolvedTypeWithMembers typeWithMembers = context.getTypeContext().resolveWithMembers(resolvedType);
        for(ResolvedField field : typeWithMembers.getMemberFields()){
            String name = field.getName();
            properties.set(name, createByField(context.getTypeContext().createFieldScope(field, typeWithMembers), actualType, context));
        }
        return properties;
    }
}
