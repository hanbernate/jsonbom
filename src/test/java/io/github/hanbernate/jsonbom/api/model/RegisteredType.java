package io.github.hanbernate.jsonbom.api.model;

public class RegisteredType {
    private String bomValue;

    private String modelClassName;

    public String getBomValue() {
        return bomValue;
    }

    public void setBomValue(String bomValue) {
        this.bomValue = bomValue;
    }

    public String getModelClassName() {
        return modelClassName;
    }

    public void setModelClassName(String modelClassName) {
        this.modelClassName = modelClassName;
    }
}
