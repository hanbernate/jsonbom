package io.github.hanbernate.jsonbom.api;

public interface ValueHandler<R> {
   R apply(Object model, String bomValue);
}
