package io.github.hanbernate.jsonbom.api;

public interface BomAdapter {
    Bom transformBom(Bom targetBom, Class<?> targetType);
}
