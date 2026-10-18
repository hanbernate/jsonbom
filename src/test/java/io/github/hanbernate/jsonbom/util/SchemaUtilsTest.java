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

    /** Raw subclass: CR site 3 — getGenericSuperclass() returns a plain Class. */
    @SuppressWarnings("rawtypes")
    public static class RawChild extends Base { }

    public static class Shapes {
        List<Child> normal;
        List rawList;
        List<? extends Child> wildcardUpper;
        List<?> wildcardUnbounded;
        List<List<Child>> nested;
        List<String>[] genericArray;
        /** CR #6 leftover: the collection element itself is a GenericArrayType, so
         *  the (Class<T>) cast at the last line of getGenericType CCEs. */
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
        return SchemaUtils.resolveActulaType(f, parent, mapping, f.getType());
    }

    @Test
    public void genericArrayTypeFailsWithJsonBomException() throws Exception {
        // List<String>[] -> GenericArrayType, the (ParameterizedType) cast at line 67 CCEs
        JsonBomException ex = assertThrows(JsonBomException.class,
                () -> resolve(Shapes.class.getDeclaredField("genericArray"), Shapes.class));
        assertEquals("Generic array is not supported.", ex.getMessage());
    }

    @Test
    public void wildcardUpperBoundFailsWithJsonBomException() throws Exception {
        // List<? extends Child> -> WildcardType argument, the (Class<T>) cast at line 72 CCEs
        JsonBomException ex = assertThrows(JsonBomException.class,
                () -> resolve(Shapes.class.getDeclaredField("wildcardUpper"), Shapes.class));
        assertEquals("WildcardType is not supported. Plaes use @BomMapping(genericType=*) to specify generic type instead.", ex.getMessage());
    }

    @Test
    public void unboundedWildcardFailsWithJsonBomException() throws Exception {
        JsonBomException ex = assertThrows(JsonBomException.class,
                () -> resolve(Shapes.class.getDeclaredField("wildcardUnbounded"), Shapes.class));
        assertEquals("WildcardType is not supported. Plaes use @BomMapping(genericType=*) to specify generic type instead.", ex.getMessage());
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
        // Base<T> inspected "as itself": T cannot be bound -> resolver returns the
        // TypeVariable and the (Class<T>) cast at line 70 CCEs -> wrapped
        JsonBomException ex = assertThrows(JsonBomException.class,
                () -> resolve(Base.class.getDeclaredField("list"), Base.class));
        assertEquals("Fail parsing generic type:T", ex.getMessage());
    }

    @Test
    public void rawSuperclassFailsWithJsonBomExceptionAndKeepsCceCause() throws Exception {
        // RawChild extends Base (raw): line 90 casts Base.class to ParameterizedType and
        // CCEs; the recursive call at line 69 is inside the caller's try -> wrapped.
        // This is the only test that pins the cause chain; the others assert just the
        // JsonBomException contract so they survive a future degrade-instead-of-throw refactor.
        JsonBomException ex = assertThrows(JsonBomException.class,
                () -> resolve(Base.class.getDeclaredField("list"), RawChild.class));
        assertEquals("Fail parsing generic type:io.github.hanbernate.jsonbom.util.SchemaUtilsTest$Base", ex.getMessage());
    }

    @Test
    public void bareTypeVariableFieldThrowsUnwrappedClassCastException() throws Exception {
        // Pins CURRENT behavior (the one CR #6 site left open): SchemaUtils line 76
        // casts the field's own generic TypeVariable to ParameterizedType with no
        // instanceof guard, so a raw CCE escapes instead of a JsonBomException.
        // When a guard is added, switch this to assertThrows(JsonBomException.class, ...).
        Field f = BoundedHolder.class.getDeclaredField("payload");
        JsonBomException ex = assertThrows(JsonBomException.class, () -> resolve(f, BoundedHolder.class));
        assertEquals("Fail parsing generic type:T" , ex.getMessage());
    }

    // ---------------- CR #6 follow-up: site the first test batch missed ----------------

    @Test
    public void elementGenericArrayFailsWithJsonBomException() throws Exception {
        // List<List<String>[]> -> the type argument is itself a GenericArrayType.
        // It is neither TypeVariable nor ParameterizedType nor WildcardType, so the
        // final (Class<T>) cast in getGenericType used to CCE — the escaped raw
        // ClassCastException was a leftover CR #6 site. Now guarded.
        JsonBomException ex = assertThrows(JsonBomException.class,
                () -> resolve(Shapes.class.getDeclaredField("elementGenericArray"), Shapes.class));
        assertEquals("Generic array is not supported.", ex.getMessage());
    }
}

