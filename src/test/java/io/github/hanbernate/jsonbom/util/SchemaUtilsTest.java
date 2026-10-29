package io.github.hanbernate.jsonbom.util;

import io.github.hanbernate.jsonbom.api.BomMapping;
import io.github.hanbernate.jsonbom.api.JsonBomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SchemaUtils generic type resolution test")
public class SchemaUtilsTest {

    // ---------------- fixtures ----------------

    public static class Child {
        Integer value;
        public void setValue(Integer value) { this.value = value; }
    }

    /** Generic holder whose element type is declared via type variable {@code T}. */
    public static class Base<T> {
        List<T> list;
        public void setList(List<T> list) { this.list = list; }
    }

    /** Fully parameterized subclass: {@code T} must resolve to {@link Child}. */
    public static class TypedChild extends Base<Child> { }

    /** Raw subclass: getGenericSuperclass() returns a plain Class, so the type variable cannot be resolved. */
    @SuppressWarnings("rawtypes")
    public static class RawChild extends Base { }

    public static class Shapes {
        List<Child> normal;
        List rawList;
        List<? extends Child> wildcardUpper;
        List<?> wildcardUnbounded;
        List<List<Child>> nested;
        List<String>[] genericArray;
        /** The collection element is itself a GenericArrayType, which the resolver rejects. */
        List<List<String>[]> elementGenericArray;
        int[] primitiveArray;
        Integer notCollection;

        @BomMapping(value="annotatedWildcard", genericType = Child.class)
        List<? extends Child> annotatedWildcard;
    }

    /** Bare type variable bounded to a collection: erasure is List, so the resolver
     *  enters the Collection branch, but the field's own generic type is a
     *  TypeVariable — not a ParameterizedType. */
    public static class BoundedHolder<T extends List<Child>> {
        T payload;
        public void setPayload(T payload) { this.payload = payload; }
    }

    private static Class<?> resolve(Field f, Class<?> parent) {
        BomMapping mapping = f.getAnnotation(BomMapping.class);
        return SchemaUtils.resolveActualType(f, parent, mapping, f.getType());
    }

    @Test
    public void genericArrayTypeFailsWithJsonBomException() throws Exception {
        // List<String>[] -> the field's generic type is a GenericArrayType, rejected by the resolver
        JsonBomException ex = assertThrows(JsonBomException.class,
                () -> resolve(Shapes.class.getDeclaredField("genericArray"), Shapes.class));
        assertEquals("Generic array is not supported.", ex.getMessage());
    }

    @Test
    public void wildcardUpperBoundFailsWithJsonBomException() throws Exception {
        // List<? extends Child> -> wildcard type argument, rejected by the resolver
        JsonBomException ex = assertThrows(JsonBomException.class,
                () -> resolve(Shapes.class.getDeclaredField("wildcardUpper"), Shapes.class));
        assertEquals("WildcardType is not supported. Please use @BomMapping(genericType=*) to specify generic type instead.", ex.getMessage());
    }

    @Test
    public void unboundedWildcardFailsWithJsonBomException() throws Exception {
        JsonBomException ex = assertThrows(JsonBomException.class,
                () -> resolve(Shapes.class.getDeclaredField("wildcardUnbounded"), Shapes.class));
        assertEquals("WildcardType is not supported. Please use @BomMapping(genericType=*) to specify generic type instead.", ex.getMessage());
    }

    @Test
    public void nestedParameterizedFailsWithJsonBomException() throws Exception {
        // List<List<Child>> -> argument is ParameterizedType, not Class
        JsonBomException ex = assertThrows(JsonBomException.class,
                () -> resolve(Shapes.class.getDeclaredField("nested"), Shapes.class));
        assertEquals("Nest generic type is not supported.", ex.getMessage());
    }

    @Test
    public void unresolvedTypeVariableFailsWithJsonBomException() throws Exception {
        // Base<T> inspected "as itself": T cannot be bound, so resolution fails
        // with "Fail parsing generic type:T"
        JsonBomException ex = assertThrows(JsonBomException.class,
                () -> resolve(Base.class.getDeclaredField("list"), Base.class));
        assertEquals("Fail parsing generic type:T", ex.getMessage());
    }

    @Test
    public void rawSuperclassFailsWithJsonBomException() throws Exception {
        // RawChild extends Base (raw): the generic superclass is not a ParameterizedType,
        // so the type variable cannot be resolved and the resolver reports failure.
        JsonBomException ex = assertThrows(JsonBomException.class,
                () -> resolve(Base.class.getDeclaredField("list"), RawChild.class));
        assertEquals("Fail parsing generic type:io.github.hanbernate.jsonbom.util.SchemaUtilsTest$Base", ex.getMessage());
    }

    @Test
    public void bareTypeVariableFieldFailsWithJsonBomException() throws Exception {
        // T extends List<Child>: erasure is List so the Collection branch is entered,
        // but the field's own generic type is a bare TypeVariable that cannot be bound.
        Field f = BoundedHolder.class.getDeclaredField("payload");
        JsonBomException ex = assertThrows(JsonBomException.class, () -> resolve(f, BoundedHolder.class));
        assertEquals("Fail parsing generic type:T" , ex.getMessage());
    }

    // ---------------- more unsupported generic shapes ----------------

    @Test
    public void elementGenericArrayFailsWithJsonBomException() throws Exception {
        // List<List<String>[]> -> the type argument is itself a GenericArrayType,
        // which is not a Class/TypeVariable/ParameterizedType/WildcardType and is rejected.
        JsonBomException ex = assertThrows(JsonBomException.class,
                () -> resolve(Shapes.class.getDeclaredField("elementGenericArray"), Shapes.class));
        assertEquals("Generic array is not supported.", ex.getMessage());
    }

    // ---------------- happy paths ----------------

    @Test
    public void concreteTypeArgumentResolvesDirectly() throws Exception {
        // List<Child> -> the type argument is a plain Class and resolves directly.
        assertEquals(Child.class, resolve(Shapes.class.getDeclaredField("normal"), Shapes.class));
    }

    @Test
    public void annotationGenericTypeTakesPriorityOverWildcard() throws Exception {
        // @BomMapping(genericType = Child.class) on List<? extends Child> -> resolved from the
        // annotation's genericType before the resolver ever inspects the wildcard.
        assertEquals(Child.class, resolve(Shapes.class.getDeclaredField("annotatedWildcard"), Shapes.class));
    }
}

