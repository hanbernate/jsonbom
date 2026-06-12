package io.github.hanbernate.jsonbom.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Bom test")
public class BomTest {
    
    @Test
    public void testCreateBomFromMap(){
        Map<String, BomOrValue> map = Map.of("a", BomOrValue.EMPTY, "b", BomOrValue.EMPTY);
        Bom bom = Bom.createBomFromMap(map);
        assertEquals(2, bom.size());
    }
}
