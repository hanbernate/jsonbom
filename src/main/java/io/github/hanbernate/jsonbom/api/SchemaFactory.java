package io.github.hanbernate.jsonbom.api;

import java.lang.reflect.Field;
import java.util.function.Function;

public interface SchemaFactory {

    String setSeprator(String seprator);

    <T> Schema<T> getByType(Class<T> clazz);

    Function<Field, String> setNameParser(Function<Field, String> nameParser);

    ValueHandlers setValueHandlers(ValueHandlers valueHandlers);
}
