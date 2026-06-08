package io.github.hanbernate.jsonbom.api;


import java.lang.reflect.Method;
import java.util.*;

public class Schema<R>{
    List<String> path = Collections.emptyList();
    String name;
    Class<R> actualType;
    Method writeMethod;
    Method readMeothd;

    Class<?> responseType;
    ValueHandler<R> valueHandler;
    Map<String, Schema<?>> children = Collections.emptyMap();

    Schema(){}

    public List<String> getPath() {
        return path;
    }

    void setPath(List<String> path) {
        this.path = path;
    }

    public Method getWriteMethod() {
        return writeMethod;
    }

    void setWriteMethod(Method writeMethod) {
        this.writeMethod = writeMethod;
    }

    public Method getReadMeothd() {
        return readMeothd;
    }

    public void setReadMeothd(Method readMeothd) {
        this.readMeothd = readMeothd;
    }

    public Class<R> getActualType() {
        return actualType;
    }

    public void setActualType(Class<R> actualType) {
        this.actualType = actualType;
    }

    public String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    public ValueHandler<R> getValueHandler() {
        return valueHandler;
    }

    void setValueHandler(ValueHandler<R> valueHandler) {
        this.valueHandler = valueHandler;
    }

    public Map<String, Schema<?>> getChildren() {
        return children;
    }

    void setChildren(Map<String, Schema<?>> children) {
        this.children = children;
    }

    public Class<?> getResponseType() {
        return responseType;
    }

    void setResponseType(Class<?> responseType) {
        this.responseType = responseType;
    }

    public boolean isResponseCollection(){
        return responseType.isArray() || Collection.class.isAssignableFrom(responseType);
    }
}
