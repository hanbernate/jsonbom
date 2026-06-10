package io.github.hanbernate.jsonbom.api;
/**
 * A record representing either a leaf value or a nested BOM (Bill of Materials) structure.
 * <p>
 * This is a discriminated union type that can hold either:
 * <ul>
 *     <li>A string {@code value} (leaf node) - when the BOM contains a primitive value</li>
 *     <li>A {@code Bom} object (nested structure) - when the BOM contains child nodes</li>
 * </ul>
 * <p>
 * Only one of the two fields will be non-null at any given time. The {@link #getType()}
 * method can be used to determine which type this instance represents.
 *
 * @param value the string value if this instance represents a leaf node; otherwise {@code null}
 * @param bom the nested BOM structure if this instance represents a container node; otherwise {@code null}
 * @author hanbernate
 * @since 0.0.1
 */
public record BomOrValue(String value, Bom bom) {

    /**
     * Returns the type of this instance.
     *
     * @return {@link Type#BOM} if this instance contains a nested BOM structure,
     *         {@link Type#VALUE} if this instance contains a leaf string value
     * @since 0.0.1
     */
    public Type getType(){
        if(null != bom){
            return Type.BOM;
        }
        return Type.VALUE;
    }
    
    /**
     * Retrieves the child BomOrValue associated with the specified key from the nested BOM.
     *
     * @param key the key to look up in the nested BOM
     * @return the BomOrValue associated with the key, or {@code null} if the key does not exist
     * @throws JsonBomException if this instance is of type VALUE (i.e., no nested BOM exists),
     *                          as child access is only allowed on BOM-type nodes
     * @since 0.0.1
     */
    public BomOrValue get(String key) throws JsonBomException{
        if(null == bom){
            throw new JsonBomException("Cannot get child bom in value node.");
        }
        return this.bom.get(key);
    }

    /**
     * An empty sentinel instance representing the absence of a value or BOM.
     * Both fields are {@code null}.
     */
    public static BomOrValue EMPTY = new BomOrValue(null, null);
}
