package io.github.hanbernate.jsonbom.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface BomMapping {
    String value();

    Class<?> genericType() default Void.class;

    Class<? extends ValueHandler> valueHandler() default ValueHandler.class;

    boolean valueNode() default false;
}
