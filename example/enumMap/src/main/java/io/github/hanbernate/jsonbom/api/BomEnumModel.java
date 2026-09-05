package io.github.hanbernate.jsonbom.api;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class BomEnumModel<T extends Enum<T> & BomModelField> implements BomModel{
    private Map<String, Publisher<?>> models = new HashMap<>();

    public BomEnumModel<T> set(T modelField, Publisher<?> publisher){
        this.models.put(modelField.toModelName(), publisher);
        return this;
    } 

    public Publisher<?> get(T modelField){
        return this.models.get(modelField.toModelName());
    }

    @Override
    public Map<String, Publisher<?>> getModels(){
        return this.models;
    }

    public <R> Mono<R> fillModel(T modelField, Mono<Bom> modelBom, Function<Mono<Bom>, Mono<R>> func){
        Mono<R> result = modelBom.map(bom -> modelField.toModelBom(bom))
            .flatMap(bom -> func.apply(Mono.just(bom))).cache();
        this.set(modelField, result);
        return result;
    }
}
