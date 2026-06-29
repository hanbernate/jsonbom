package io.github.hanbernate.jsonbom.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

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
        Bom bom = Bom.createWithEmptyValue("a","b","c","d");
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

    @Test
    public void testMergeWithEmpty(){
        Bom bom = new Bom();
        bom.mergeWithEmpty("key1");
        assertEquals(1, bom.size());
        assertEquals(BomOrValue.EMPTY, bom.get("key1"));
    }

    @Test
    public void testMergeOtherBom(){
        Bom bom = new Bom();
        bom.merge("a", BomOrValue.EMPTY);

        Bom other = new Bom();
        other.merge("b", BomOrValue.EMPTY);
        other.merge("c", BomOrValue.EMPTY);

        bom.mergeOtherBom(other);
        assertEquals(3, bom.size());
        assertNotNull(bom.get("a"));
        assertNotNull(bom.get("b"));
        assertNotNull(bom.get("c"));
    }

    @Test
    public void testClone(){
        Bom sub = new Bom();
        sub.merge("x", BomOrValue.EMPTY);
        Bom original = new Bom();
        original.merge("a", new BomOrValue(null, sub));
        original.merge("b", BomOrValue.EMPTY);

        Bom cloned = original.clone();
        assertEquals(original.size(), cloned.size());
        assertNotNull(cloned.getBom("a"));
        assertNotSame(original.getBom("a"), cloned.getBom("a"));
    }
}
