package io.github.hanbernate.jsonbom.api;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of the BomAdapter interface.
 * <p>
 * This implementation transforms a BOM structure by traversing the source BOM
 * and reorganizing its fields according to the path definitions in the target
 * type's schema. It handles both leaf values and nested BOM structures recursively.
 * <p>
 *
 * @author hanbernate
 * @since 0.0.1
 */
public class DefaultBomAdapterImpl implements BomAdapter{
    private static final Logger logger = LoggerFactory.getLogger(DefaultBomAdapterImpl.class);
    DefaultBomAdapterImpl(){

    }

    private SchemaFactory schemaFactory;

    void setSchemaFactory(SchemaFactory schemaFactory){
        this.schemaFactory = schemaFactory;
    }

    /**
     * {@inheritDoc}
     *
     * @param bom the source BOM to transform
     * @param targetType the target class type whose schema defines the transformation rules
     * @return a new BOM instance with fields organized according to the target type's schema
     * @since 0.0.1
     */
    @Override
    public Bom transformBom(Bom bom, Class<?> targetType) {
        Schema<?> targetSchema = schemaFactory.getByType(targetType);
        Bom result = new Bom();

        for(Map.Entry<String, BomOrValue> entry : bom.entrySet()) {
            Schema<?> childSchema = targetSchema.getChildren().get(entry.getKey());
            BomOrValue bomOrValue = entry.getValue();
            if(null != childSchema){
                // Recursively transform the value according to the child schema
                BomOrValue targetBomOrValue = visit(bomOrValue, childSchema, 1);
                String key = childSchema.getPath().get(0);
                result.merge(key, targetBomOrValue);
            }else{
                // If no childSchema exists, current bom node will be skipped.
                logger.debug("Unknown field '{}' ignored during BOM transformation", entry.getKey());
            }
        }
        return result;
    }

    /**
     * Visits and transforms a BomOrValue node. Dispatches based on whether the node
     * is a leaf value or a nested BOM structure.
     *
     * @param bomOrValue the node to transform
     * @param bomSchema the schema defining the target structure
     * @param startIdx the starting index for path wrapping (1 = skip root, 0 = include root)
     * @return the transformed node wrapped with the appropriate path hierarchy
     */
    private BomOrValue visit(BomOrValue bomOrValue, Schema<?> bomSchema, int startIdx){
        if(Type.VALUE == bomOrValue.getType()){
            // Leaf value: wrap the value with the path hierarchy
            return wrapPath(new BomOrValue(bomOrValue.value(), null), bomSchema, startIdx);
        }else{
            // Nested BOM: recursively transform the inner BOM first, then wrap
            return wrapPath(new BomOrValue(null, visitBom(bomOrValue, bomSchema)), bomSchema, startIdx);
        }
    }

    /**
     * Recursively transforms all entries within a nested BOM structure.
     * <p>
     * This method is called when a BomOrValue contains a BOM (Type.BOM).
     * It applies the same transformation logic to each child entry.
     *
     * @param bomOrValue the BomOrValue containing the nested BOM to transform
     * @param bomSchema the schema defining transformation rules for the BOM structure
     * @return a new transformed Bom instance
     */
    private Bom visitBom(BomOrValue bomOrValue, Schema<?> bomSchema){
        Bom result = new Bom();
        for(Map.Entry<String, BomOrValue> entry : bomOrValue.bom().entrySet()){
            Schema<?> childSchema = bomSchema.getChildren().get(entry.getKey());
            if(null != childSchema){
                // The modelKey is the first element in the child's path (the root key for this subtree)
                String modelKey = childSchema.getPath().get(0);
                BomOrValue childBomOrValue = visit(entry.getValue(), childSchema, 1);
                result.merge(modelKey, childBomOrValue);
            }
        }
        return result;
    }
    /**
     * Wraps a leaf BomOrValue node with nested BOM structures to create the full path hierarchy.
     * <p>
     * Example: Given a leaf value "V", a path ["a", "b", "c"], and startIdx = 1,
     * this method produces: { "b": { "c": "V" } } 
     *  <p>
     * Note: The root key ("a") is not wrapped by this method but is handled by the caller
     * via {@code childSchema.getPath().get(0)} and {@link Bom#merge(String, BomOrValue)}.
     * The final merged result becomes: { "a": { "b": { "c": "V" } } }
     * 
     * @param leaf the leaf node to wrap (may be a value or already transformed BOM)
     * @param bomSchema the schema containing the path definition
     * @param startIdx the starting index in the path (inclusive). Use 1 to skip the root
     *                 (which is handled separately by the caller), or 0 to include all levels.
     * @return the wrapped BomOrValue node with the full path hierarchy applied
     */
    private BomOrValue wrapPath(BomOrValue leaf, Schema<?> bomSchema, int startIdx){
        BomOrValue current = leaf;
        // Traverse the path from the deepest level (end) back to the start index
        // Example: path = ["a", "b", "c"], size=3, startIdx=1
        // Iteration 1: i=2 -> wrap with "c" : { "c": "V" })
        // Iteration 2: i=1 -> wrap with "b" : { "b": { "c": "V" } }
        // Iteration stops at i=0 (excluded) when startIdx=1
        for(int i = bomSchema.getPath().size() - 1; i >= startIdx ; i--){
            Bom bom = new Bom();
            String key = bomSchema.getPath().get(i);
            bom.putIfAbsent(key, current);
            current = new BomOrValue(null, bom);
        }
        return current;

    }
}
