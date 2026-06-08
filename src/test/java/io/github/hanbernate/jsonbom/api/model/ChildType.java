package io.github.hanbernate.jsonbom.api.model;

import io.github.hanbernate.jsonbom.api.BomMapping;
import io.github.hanbernate.jsonbom.api.Type;

import java.util.List;
import java.util.Set;

public class ChildType {
    @BomMapping("primitive")
    private int primitive;

    @BomMapping("boxed")
    private Integer boxed;

    @BomMapping("type")
    Type type;

    @BomMapping("string")
    String string;

    @BomMapping(value = "collection", genericType = ChildType.class)
    List<ChildType> list;

    @BomMapping(value = "collection", genericType = ChildType.class)
    Set<ChildType> set;

    @BomMapping(value = "collection", genericType = ChildType.class)
    ChildType[] array;

    @BomMapping(value = "collection/0", genericType = ChildType.class)
    ChildType first;

    @BomMapping(value = "", genericType = ChildType.class)
    ChildType emptyPath;

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

    public List<ChildType> getList() {
        return list;
    }

    public void setList(List<ChildType> list) {
        this.list = list;
    }

    public Set<ChildType> getSet() {
        return set;
    }

    public void setSet(Set<ChildType> set) {
        this.set = set;
    }

    public ChildType[] getArray() {
        return array;
    }

    public void setArray(ChildType[] array) {
        this.array = array;
    }

    public ChildType getFirst() {
        return first;
    }

    public void setFirst(ChildType first) {
        this.first = first;
    }

    public ChildType getEmptyPath() {
        return emptyPath;
    }

    public void setEmptyPath(ChildType emptyPath) {
        this.emptyPath = emptyPath;
    }
}
