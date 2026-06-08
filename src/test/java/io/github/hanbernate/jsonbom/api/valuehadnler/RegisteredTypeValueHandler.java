package io.github.hanbernate.jsonbom.api.valuehadnler;

import io.github.hanbernate.jsonbom.api.ValueHandler;
import io.github.hanbernate.jsonbom.api.model.RegisteredType;

public class RegisteredTypeValueHandler implements ValueHandler<RegisteredType> {
    @Override
    public RegisteredType apply(Object model, String bomValue) {
        RegisteredType result = new RegisteredType();
        result.setBomValue(bomValue);
        result.setModelClassName(model.getClass().getName());
        return result;
    }
}
