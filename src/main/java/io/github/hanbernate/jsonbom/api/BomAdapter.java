package io.github.hanbernate.jsonbom.api;

/**
 * BOM adapter interface for transforming a Bill of Materials (BOM) structure
 * according to a target type's schema.
 * <p>
 * This adapter is responsible for converting a raw BOM into a structurally
 * transformed BOM that conforms to the field paths defined by the target type's schema.
 * <p>
 * This API has been established since version 0.0.1.
 *
 * @author hanbernate
 * @since 0.0.1
 */
public interface BomAdapter {

    /**
     * Transforms the source BOM into a new BOM structure that conforms to the
     * schema of the specified target type.
     * <p>
     * The transformation re-organizes the BOM's fields according to the path
     * definitions in the target type's schema, creating nested BOM structures
     * as needed.
     *
     * @param targetBom the source BOM to transform
     * @param targetType the target class type whose schema defines the transformation rules
     * @return a new BOM instance with fields organized according to the target type's schema
     * @since 0.0.1
     */
    Bom transformBom(Bom targetBom, Class<?> targetType);
    
    /**
     * Factory method to initialize and create a BomAdapter instance.
     *
     * @param schemaFactory the SchemaFactory to be used for retrieving type schemas
     * @return a newly initialized BomAdapter instance
     * @since 0.0.1
     */
    public static BomAdapter init(SchemaFactory schemaFactory){
        DefaultBomAdapterImpl instance = new DefaultBomAdapterImpl();
        instance.setSchemaFactory(schemaFactory);
        return instance;
    }
}
