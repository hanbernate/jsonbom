package io.github.hanbernate.jsonbom.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Bom test")
public class BomTest {
    
    @Test
    public void testCreateBomFromMap(){
        Map<String, BomOrValue> map = Map.of("a", BomOrValue.EMPTY, "b", BomOrValue.EMPTY);
        Bom bom = Bom.createFromMap(map);
        assertEquals(2, bom.size());
    }
    
    @Test
    public void testCreateWithEmptyValue4Collection(){
        Bom bom = Bom.createWithEmptyValue(List.of("a","b","c"));
        assertEquals(3, bom.size());
    }
    
    @Test
    public void testCreateWithEmptyValue4Array(){
        Bom bom = Bom.createWithEmptyValue(List.of("a","b","c","d"));
        assertEquals(4, bom.size());
    }
    
    @Test
    public void testGetBom(){
        Bom aSub = Bom.createFromMap(Map.of("a1", BomOrValue.EMPTY, "a2", BomOrValue.EMPTY));
        Map<String, BomOrValue> map = Map.of("a", new BomOrValue(null, aSub), "b", BomOrValue.EMPTY);
        Bom bom = Bom.createFromMap(map);
        assertEquals(2, bom.size());
        assertEquals(aSub, bom.getBom("a"));
        assertNull(bom.getBom("c"));
    }
}
