package io.github.hanbernate.jsonbom.jackson;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.reflect.Field;
import java.util.function.Function;

public class JacksonNameParser implements Function<Field, String> {
    @Override
    public String apply(Field field) {
        JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
        return null != jsonProperty ? jsonProperty.value() : field.getName();
    }
}
