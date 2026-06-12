package io.github.hanbernate.jsonbom.api;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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

    /**
     * Creates a {@link Bom} instance from a {@link Map} of {@link BomOrValue} entries.
     * <p>
     * This factory method streams the map entries and collects them into a new {@link Bom}
     *
     * @param map the source map containing {@code String} keys and {@link BomOrValue} values;
     *            must not be {@code null}
     * @return a new {@link Bom} instance populated with the merged entries from the map
     * @since 0.0.2
     */
    public static Bom createFromMap(Map<String, BomOrValue> map){
        return map.entrySet().stream()
            .collect(new BomCollectorImpl<>(Entry::getKey, Entry::getValue));
    }

    /**
     * Creates a {@link Bom} instance where each key maps to an empty value.
     * <p>
     * This factory method initializes a BOM with the specified keys, each associated
     * with {@link BomOrValue#EMPTY} as a placeholder. The resulting BOM can be used
     * as a structural skeleton that is later populated via {@link #merge(String, BomOrValue)}.
     *
     * @param keys the collection of keys to initialize in the BOM;
     *             must not be {@code null}
     * @return a new {@link Bom} instance containing each key mapped to an empty value
     * @since 0.0.2
     */
    public static Bom createWithEmptyValue(Collection<String> keys){
        return keys.stream()
            .collect(new BomCollectorImpl<>(k -> k, k -> BomOrValue.EMPTY));
    }

    /**
     * Creates a {@link Bom} instance where each key maps to an empty value.
     * <p>
     * This factory method initializes a BOM with the specified keys, each associated
     * with {@link BomOrValue#EMPTY} as a placeholder. The resulting BOM can be used
     * as a structural skeleton that is later populated via {@link #merge(String, BomOrValue)}.
     *
     * @param keys the collection of keys to initialize in the BOM;
     *             must not be {@code null}
     * @return a new {@link Bom} instance containing each key mapped to an empty value
     * @since 0.0.2
     */
    public static Bom createWithEmptyValue(String... keys){
        return Arrays.stream(keys)
            .collect(new BomCollectorImpl<>(k -> k, k -> BomOrValue.EMPTY));
    }
}
