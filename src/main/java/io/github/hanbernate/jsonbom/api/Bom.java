package io.github.hanbernate.jsonbom.api;

import java.util.HashMap;

public class Bom extends HashMap<String, BomOrValue>{

    public Bom merge(String key, BomOrValue value){
        if(!this.containsKey(key)){
            this.put(key, value);
            return this;
        }
        BomOrValue existValue = this.get(key);
        if(Type.VALUE == existValue.getType()){
            return this;
        }

        Bom exists = existValue.bom();

        value.bom().entrySet().stream()
            .forEach(entry ->{
                exists.merge(entry.getKey(), entry.getValue());
            });
        return this;
    }
}
