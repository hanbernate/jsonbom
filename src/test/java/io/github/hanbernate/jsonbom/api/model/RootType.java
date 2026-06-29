package io.github.hanbernate.jsonbom.api.model;

import io.github.hanbernate.jsonbom.api.BomMapping;
import io.github.hanbernate.jsonbom.api.Type;
import io.github.hanbernate.jsonbom.api.valuehadnler.DateTimeFormatValueHandler;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Set;

public class RootType {
    @BomMapping("model")
    private ChildType child;

    @BomMapping("primitive")
    private int primitive;

    @BomMapping("boxed")
    private Integer boxed;

    @BomMapping("type")
    Type type;

    @BomMapping("string")
    String string;

    @BomMapping(value = "children", genericType = ChildType.class)
    List<ChildType> children;

    @BomMapping(value = "children", genericType = ChildType.class)
    Set<ChildType> childrenSet;

    @BomMapping(value = "datetime", valueHandler = DateTimeFormatValueHandler.class)
    private String datetime;

    @BomMapping("model")
    private RegisteredType registered;

    @JsonProperty("alias")
    @BomMapping("model")
    private ChildType origin;

    public ChildType getChild() {
        return child;
    }

    public void setChild(ChildType child) {
        this.child = child;
    }

    public int getPrimitive() {
        return primitive;
    }

    public void setPrimitive(int primitive) {
        this.primitive = primitive;
    }

    public Integer getBoxed() {
        return boxed;
    }

    public void setBoxed(Integer boxed) {
        this.boxed = boxed;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getString() {
        return string;
    }

    public void setString(String string) {
        this.string = string;
    }

    public List<ChildType> getChildren() {
        return children;
    }

    public void setChildren(List<ChildType> children) {
        this.children = children;
    }

    public Set<ChildType> getChildrenSet() {
        return childrenSet;
    }

    public void setChildrenSet(Set<ChildType> childrenSet) {
        this.childrenSet = childrenSet;
    }

    public void setDatetime(String datetime){
        this.datetime = datetime;
    }

    public String getDatetime(){
        return this.datetime;
    }

    public RegisteredType getRegistered() {
        return registered;
    }

    public void setRegistered(RegisteredType registered) {
        this.registered = registered;
    }

    public ChildType getOrigin() {
        return origin;
    }

    public void setOrigin(ChildType origin) {
        this.origin = origin;
    }
}
