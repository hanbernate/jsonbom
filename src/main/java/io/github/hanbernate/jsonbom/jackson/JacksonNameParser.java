package io.github.hanbernate.jsonbom.jackson;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.reflect.Field;
import java.util.function.Function;
/**
 * A name parser that extracts the JSON property name from a Java field using
 * Jackson's {@link JsonProperty} annotation.
 * <p>
 * This parser implements {@link Function&lt;Field, String&gt;} and is used by
 * the schema factory to determine the BOM path name for a field.
 * <p>
 * The naming logic is:
 * <ul>
 *     <li>If the field is annotated with {@code @JsonProperty}, use the annotation's value</li>
 *     <li>Otherwise, use the field's declared name</li>
 * </ul>
 *
 * @author hanbernate
 * @since 0.0.1
 */
public class JacksonNameParser implements Function<Field, String> {
    /**
     * Returns the JSON property name for the given field.
     * <p>
     * This method checks for the presence of the {@link JsonProperty} annotation
     * and returns its value if present. If no annotation is found, the field's
     * declared name is returned.
     *
     * @param field the field to extract the property name from
     * @return the JSON property name (either from {@code @JsonProperty} or the field name)
     * @since 0.0.1
     */
    @Override
    public String apply(Field field) {
        JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
        return null != jsonProperty ? jsonProperty.value() : field.getName();
    }
}
