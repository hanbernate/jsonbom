package io.github.hanbernate.jsonbom.example;

import io.github.hanbernate.jsonbom.api.BomModelField;

public enum OrderModelFieldEnum implements BomModelField{
    ORDER("order", OrderRepository.Order.class),
    ORDER_LOG("orderLog", OrderLogRepository.OrderLog.class),
    ;
    private String modelName;
    private Class<?> actualClass;
    private OrderModelFieldEnum(String modelName, Class<?> actualClass){
        this.modelName = modelName;
        this.actualClass = actualClass;
    }

    public String toModelName(){
        return this.modelName;
    }
    
}