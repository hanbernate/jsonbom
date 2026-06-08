package io.github.hanbernate.jsonbom.api;

public interface ValueHandlers {
    ValueHandler<?> registry(Class<?> type, ValueHandler<?> valueHandler);

    ValueHandler<?> getOrCreate(Class<? extends ValueHandler> valueHandlerClass);

    ValueHandler<?> getByResponseType(Class<?> responseType);
}
