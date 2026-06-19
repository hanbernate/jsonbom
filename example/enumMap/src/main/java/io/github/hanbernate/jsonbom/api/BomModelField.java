package io.github.hanbernate.jsonbom.api;

import io.github.hanbernate.jsonbom.api.Bom;

public interface BomModelField {

    String toModelName();

    default Bom toModelBom(Bom sourceBom){
        return sourceBom.getBom(toModelName());
    }
}

