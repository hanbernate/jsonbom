package io.github.hanbernate.jsonbom.api;

import java.util.Map;

import org.reactivestreams.Publisher;

public interface BomModel {
    public Map<String, Publisher<?>> getModels();
}
