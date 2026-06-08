package io.github.hanbernate.jsonbom.api;

public record BomOrValue(String value, Bom bom) {

    public Type getType(){
        if(null != bom){
            return Type.BOM;
        }
        return Type.VALUE;
    }

    public BomOrValue get(String key){
        return this.bom.get(key);
    }

    public static BomOrValue EMPTY = new BomOrValue(null, null);
}
