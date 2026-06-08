package io.github.hanbernate.jsonbom.api;

import java.util.Map;

public class BomAdapterImpl implements BomAdapter{
    private BomAdapterImpl(){

    }

    public static BomAdapterImpl init(SchemaFactory schemaFactory){
        BomAdapterImpl instance = new BomAdapterImpl();
        instance.setSchemaFactory(schemaFactory);
        return instance;
    }
    private SchemaFactory schemaFactory;

    private void setSchemaFactory(SchemaFactory schemaFactory){
        this.schemaFactory = schemaFactory;
    }
    @Override
    public Bom transformBom(Bom bom, Class<?> targetType) {
        Schema<?> targetSchema = schemaFactory.getByType(targetType);
        Bom result = new Bom();

        for(Map.Entry<String, BomOrValue> entry : bom.entrySet()) {
            Schema<?> childSchema = targetSchema.getChildren().get(entry.getKey());
            BomOrValue bomOrValue = entry.getValue();
            if(null != childSchema){
                BomOrValue targetBomOrValue = visit(bomOrValue, childSchema, 1);
                String key = childSchema.getPath().get(0);
                result.merge(key, targetBomOrValue);
            }
        }
        return result;
    }


    private BomOrValue visit(BomOrValue bomOrValue, Schema<?> bomSchema, int startIdx){
        if(Type.VALUE == bomOrValue.getType()){
            return wrapPath(new BomOrValue(bomOrValue.value(), null), bomSchema, startIdx);
        }else{
            return wrapPath(new BomOrValue(null, visitBom(bomOrValue, bomSchema)), bomSchema, startIdx);
        }
    }

    private Bom visitBom(BomOrValue bomOrValue, Schema<?> bomSchema){
        Bom result = new Bom();
        for(Map.Entry<String, BomOrValue> entry : bomOrValue.bom().entrySet()){
            Schema<?> childSchema = bomSchema.getChildren().get(entry.getKey());
            String modelKey = childSchema.getPath().get(0);
            BomOrValue childlBomOrValue = visit(entry.getValue(), childSchema, 1);
            result.merge(modelKey, childlBomOrValue);
        }
        return result;
    }

    private BomOrValue wrapPath(BomOrValue leef, Schema<?> bomSchema, int startIdx){
        BomOrValue current = leef;
        for(int i = bomSchema.getPath().size() - 1; i >= startIdx ; i--){
            Bom bom = new Bom();
            String key = bomSchema.getPath().get(i);
            bom.putIfAbsent(key, current);
            current = new BomOrValue(null, bom);
        }
        return current;

    }
}
