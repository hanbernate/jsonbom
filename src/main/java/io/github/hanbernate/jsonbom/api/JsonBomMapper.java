package io.github.hanbernate.jsonbom.api;

import org.reactivestreams.Publisher;

import java.util.Map;

public interface JsonBomMapper {

    <T> Schema<T> registrySchemaIfAbsent(Class<T> responseType);

    ValueHandler<?>registryValueHandler(Class<?> responseType, ValueHandler<?> valueHandler);

    <T> Publisher<T> map(Publisher<Bom> bomPublisher, Class<T> targetType, Map<String, Publisher<?>> models);

    <T,U> Publisher<T> map(Publisher<Bom> bomPublisher, Class<T> targetType, Class<U> modelType, Map<String, Publisher<?>> sourceModels);

    BomAdapter getBomAdapter();
}
