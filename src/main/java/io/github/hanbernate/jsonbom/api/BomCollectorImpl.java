package io.github.hanbernate.jsonbom.api;

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * A custom {@link Collector} that accumulates stream elements into a {@link Bom} instance.
 * <p>
 * This collector extracts a key (of type {@code String}) and a value (of type {@link BomOrValue})
 * from each stream element using the provided {@code keyFunction} and {@code valueFunction},
 * then merges them into the target {@link Bom} via {@link Bom#merge(String, BomOrValue)}.
 * <p>
 * The collector has the following characteristics:
 * <ul>
 *     <li>{@link Characteristics#IDENTITY_FINISH} — the supplier result ({@code Bom})
 *         is used directly without transformation.</li>
 *     <li>{@link Characteristics#UNORDERED} — the order of stream elements does not
 *         affect the final result.</li>
 * </ul>
 * <p>
 * Usage example with {@link Collectors} or any stream:
 * <pre>{@code
 * Map<String, BomOrValue> map = ...;
 * Bom bom = map.entrySet().stream()
 *     .collect(new BomCollectorImpl<>(Entry::getKey, Entry::getValue));
 * }</pre>
 *
 * @param <T> the type of input elements to be collected
 * @author hanbernate
 * @since 0.0.2
 */
public class BomCollectorImpl<T> implements Collector<T, Bom , Bom>{

    private Function<T, String> keyFunction;

    private Function<T, BomOrValue> valueFunction;

    /**
     * Creates a {@code BomCollectorImpl} with the specified key and value extraction functions.
     *
     * @param keyFunction   a function that extracts the {@code String} key from each element;
     *                      must not be {@code null}
     * @param valueFunction a function that extracts the {@link BomOrValue} value from each element;
     *                      must not be {@code null}
     * @since 0.0.1
     */
    public BomCollectorImpl(Function<T, String> keyFunction, Function<T, BomOrValue> valueFunction){
        this.keyFunction = keyFunction;
        this.valueFunction = valueFunction;
    }

    /**
     * Returns a function that merges each stream element into the accumulating {@link Bom}
     * by extracting its key-value pair via the configured functions.
     *
     * @return a {@link BiConsumer} that accepts the accumulator {@link Bom} and an element
     * @since 0.0.3
     */
    @Override
    public BiConsumer<Bom, T> accumulator() {
        return (bom, t) ->{
            bom.merge(keyFunction.apply(t), valueFunction.apply(t));
        };
    }

    /**
     * Returns the set of collector characteristics.
     * <p>
     * This collector always returns {@link Characteristics#IDENTITY_FINISH} and
     * {@link Characteristics#UNORDERED}.
     *
     * @return an immutable set of characteristics
     * @since 0.0.3
     */
    @Override
    public Set<Characteristics> characteristics() {
        return Set.of(Characteristics.IDENTITY_FINISH, Characteristics.UNORDERED);
    }

    /**
     * Returns a function that merges two {@link Bom} accumulators by iterating over
     * entries of the second and merging each into the first.
     *
     * @return a {@link BinaryOperator} that combines two {@link Bom} instances
     * @since 0.0.3
     */
    @Override
    public BinaryOperator<Bom> combiner() {
        return (b1, b2) -> {
            for(Map.Entry<String, BomOrValue> e : b2.entrySet()){
                b1.merge(e.getKey(), e.getValue());
            }
            return b1;
        };
    }

    /**
     * Returns the identity finisher — the accumulator {@link Bom} is used as the final result
     * without any transformation.
     *
     * @return an identity {@link Function}
     * @since 0.0.3
     */
    @Override
    public Function<Bom, Bom> finisher() {
        return b -> b;
    }

    /**
     * Returns a supplier that creates a new empty {@link Bom} instance as the accumulation container.
     *
     * @return a {@link Supplier} of {@link Bom}
     * @since 0.0.3
     */
    @Override
    public Supplier<Bom> supplier() {
        return Bom::new;
    }
    
}
