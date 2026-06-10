package io.github.hanbernate.jsonbom.api;

import java.util.HashMap;

/**
 * Represents a Bill of Materials (BOM) node that extends HashMap with String keys
 * and BomOrValue values.
 * <p>
 * A Bom instance can contain nested BOM structures or leaf values, supporting
 * recursive merge operations for hierarchical data structures.
 * <p>
 *
 * @author hanbernate
 * @since 0.0.1
 */
public class Bom extends HashMap<String, BomOrValue>{

    /**
     * Merges a key-value pair into this BOM node.
     * <p>
     * The merge behavior depends on the existing value associated with the key:
     * <ul>
     *     <li>If the key does not exist, the new value is added directly.</li>
     *     <li>If the existing value is a leaf value (Type.VALUE), no merge is performed
     *         and this BOM remains unchanged.</li>
     *     <li>If the existing value is a nested BOM (Type.BOM), the new value's BOM
     *         entries are recursively merged into the existing BOM.</li>
     * </ul>
     *
     * @param key the key under which the value should be merged
     * @param value the value to merge (must contain either a leaf value or a nested BOM)
     * @return this Bom instance for method chaining
     * @since 0.0.1
     */
    public Bom merge(String key, BomOrValue value){
        if(!this.containsKey(key)){
            this.put(key, value);
            return this;
        }
        BomOrValue existValue = this.get(key);
        if(Type.VALUE == existValue.getType()){
            return this;
        }

        Bom exists = existValue.bom();

        value.bom().entrySet().stream()
            .forEach(entry ->{
                exists.merge(entry.getKey(), entry.getValue());
            });
        return this;
    }
}
