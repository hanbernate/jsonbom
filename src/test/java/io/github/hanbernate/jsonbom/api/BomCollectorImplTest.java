package io.github.hanbernate.jsonbom.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BomCollectorImpl test")
public class BomCollectorImplTest {

    @Test
    public void supplierShouldCreateNewBom() {
        BomCollectorImpl<Map.Entry<String, BomOrValue>> collector =
                new BomCollectorImpl<>(Map.Entry::getKey, Map.Entry::getValue);
        Bom bom = collector.supplier().get();
        assertNotNull(bom);
        assertEquals(0, bom.size());
    }

    @Test
    public void accumulatorShouldAddEntry() {
        BomCollectorImpl<Map.Entry<String, BomOrValue>> collector =
                new BomCollectorImpl<>(Map.Entry::getKey, Map.Entry::getValue);
        Bom bom = collector.supplier().get();
        collector.accumulator().accept(bom, new AbstractMap.SimpleEntry<>("key1", BomOrValue.EMPTY));
        assertEquals(1, bom.size());
        assertEquals(BomOrValue.EMPTY, bom.get("key1"));
    }

    @Test
    public void accumulatorShouldMergeDuplicateKeys() {
        BomCollectorImpl<Map.Entry<String, BomOrValue>> collector =
                new BomCollectorImpl<>(Map.Entry::getKey, Map.Entry::getValue);

        Bom sub = new Bom();
        sub.merge("subKey", BomOrValue.EMPTY);

        Bom bom = collector.supplier().get();
        // First entry with nested Bom
        collector.accumulator().accept(bom, new AbstractMap.SimpleEntry<>("key1", new BomOrValue(null, sub)));
        // Second entry merging into same key
        Bom sub2 = new Bom();
        sub2.merge("subKey2", BomOrValue.EMPTY);
        collector.accumulator().accept(bom, new AbstractMap.SimpleEntry<>("key1", new BomOrValue(null, sub2)));

        assertEquals(1, bom.size());
        BomOrValue merged = bom.get("key1");
        assertEquals(Type.BOM, merged.getType());
        assertEquals(2, merged.bom().size());
    }

    @Test
    public void combinerShouldMergeTwoBoms() {
        BomCollectorImpl<Map.Entry<String, BomOrValue>> collector =
                new BomCollectorImpl<>(Map.Entry::getKey, Map.Entry::getValue);

        Bom b1 = collector.supplier().get();
        b1.merge("a", BomOrValue.EMPTY);
        b1.merge("b", BomOrValue.EMPTY);

        Bom b2 = collector.supplier().get();
        b2.merge("c", BomOrValue.EMPTY);
        b2.merge("d", BomOrValue.EMPTY);

        Bom result = collector.combiner().apply(b1, b2);
        assertEquals(4, result.size());
        assertSame(b1, result);
    }

    @Test
    public void finisherShouldReturnIdentity() {
        BomCollectorImpl<Map.Entry<String, BomOrValue>> collector =
                new BomCollectorImpl<>(Map.Entry::getKey, Map.Entry::getValue);
        Bom bom = collector.supplier().get();
        bom.merge("a", BomOrValue.EMPTY);
        assertSame(bom, collector.finisher().apply(bom));
    }

    @Test
    public void characteristicsShouldContainIdentityFinishAndUnordered() {
        BomCollectorImpl<Map.Entry<String, BomOrValue>> collector =
                new BomCollectorImpl<>(Map.Entry::getKey, Map.Entry::getValue);
        Set<Collector.Characteristics> chars = collector.characteristics();
        assertTrue(chars.contains(Collector.Characteristics.IDENTITY_FINISH));
        assertTrue(chars.contains(Collector.Characteristics.UNORDERED));
    }

    @Test
    public void collectFromStreamShouldProduceCorrectBom() {
        Map<String, BomOrValue> source = Map.of(
                "x", BomOrValue.EMPTY,
                "y", BomOrValue.EMPTY,
                "z", BomOrValue.EMPTY
        );
        Bom bom = source.entrySet().stream()
                .collect(new BomCollectorImpl<>(Map.Entry::getKey, Map.Entry::getValue));
        assertEquals(3, bom.size());
        assertEquals(BomOrValue.EMPTY, bom.get("x"));
        assertEquals(BomOrValue.EMPTY, bom.get("y"));
        assertEquals(BomOrValue.EMPTY, bom.get("z"));
    }

    @Test
    public void collectEmptyStreamShouldReturnEmptyBom() {
        Bom bom = Stream.<Map.Entry<String, BomOrValue>>empty()
                .collect(new BomCollectorImpl<>(Map.Entry::getKey, Map.Entry::getValue));
        assertEquals(0, bom.size());
    }

    @Test
    public void collectFromParallelStreamShouldProduceCorrectBom() {
        // Use enough elements to trigger parallel processing with combiner
        List<String> keys = IntStream.range(0, 100)
                .mapToObj(i -> "key" + i)
                .collect(Collectors.toList());
        Bom bom = keys.parallelStream()
                .collect(new BomCollectorImpl<>(k -> k, k -> BomOrValue.EMPTY));
        assertEquals(100, bom.size());
        assertNotNull(bom.get("key0"));
        assertNotNull(bom.get("key99"));
    }
}
