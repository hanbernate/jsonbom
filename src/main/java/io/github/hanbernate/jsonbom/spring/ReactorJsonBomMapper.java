package io.github.hanbernate.jsonbom.spring;

import io.github.hanbernate.jsonbom.api.*;
import org.reactivestreams.Publisher;
import org.springframework.beans.BeanUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReactorJsonBomMapper implements JsonBomMapper {

    private ValueHandlers valueHandlers;

    private SchemaFactory schemaFactory;

    private BomAdapter bomAdapter;

    public ReactorJsonBomMapper(){
        BeanUtil beanUtil = new SpringBeanUtil();

        ValueHandlersImpl valueHandlers = new ValueHandlersImpl();
        valueHandlers.setBeanUtil(beanUtil);
        this.valueHandlers = valueHandlers;

        SchemaFactoryImpl schemaFactory = new SchemaFactoryImpl();
        schemaFactory.setBeanUtil(beanUtil);
        schemaFactory.setValueHandlers(this.valueHandlers);
        this.schemaFactory = schemaFactory;

        this.bomAdapter = BomAdapterImpl.init(schemaFactory);
    }

    public void setNameParser(Function<Field, String> nameParser){
        this.schemaFactory.setNameParser(nameParser);
    }

    @Override
    public <T> Schema<T> registrySchemaIfAbsent(Class<T> responseType) {
        return this.schemaFactory.getByType(responseType);
    }

    @Override
    public ValueHandler<?> registryValueHandler(Class<?> type, ValueHandler<?> valueHandler) {
        return this.valueHandlers.registry(type, valueHandler);
    }

    @Override
    public <T> Publisher<T> map(Publisher<Bom> bomPublisher, final Class<T> responseType, Map<String, Publisher<?>> models) {
        return ((Mono<Bom>) bomPublisher)
                .flatMap(bom ->{
                    Schema<T> reponseSchema = registrySchemaIfAbsent(responseType);
                    Mono<T> result = Mono.just(BeanUtils.instantiateClass(responseType));
                    for(Map.Entry<String, BomOrValue> entry : bom.entrySet()){
                        Schema<?> childSchema = reponseSchema.getChildren().get(entry.getKey());
                        BomOrValue child = entry.getValue();
                        Mono<?> fieldPublisher = visit(models, child, childSchema);
                        result = result.zipWith(fieldPublisher, (r, v) ->{
                            if(null != v){
                                try {
                                    childSchema.getWriteMethod().invoke(r, v);
                                } catch (IllegalAccessException | InvocationTargetException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                            return r;
                        });

                    }

                    return result;
                });
    }

    private <T> Mono<T> visit(Map<String, Publisher<?>> models, BomOrValue bomOrValue, Schema<T> reponseSchema){
        if(null == reponseSchema){
            return Mono.empty();
        }

        String path = 0 == reponseSchema.getPath().size() ? "" : reponseSchema.getPath().get(0);
        Publisher<?> model = models.get(path);
        if(model instanceof Flux<?>){
            Flux<?> fluxResult = ((Flux<?>) model).cache().map(m -> {
                try {
                    return visit(bomOrValue, m, reponseSchema, 1, true);
                } catch (InvocationTargetException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            });

            if (reponseSchema.getResponseType().isArray()) {
                throw new RuntimeException();
            }

            if (List.class.isAssignableFrom(reponseSchema.getResponseType())) {
                return (Mono<T>) fluxResult.collect(Collectors.toList());
            }

            if (Set.class.isAssignableFrom(reponseSchema.getResponseType())) {
                return (Mono<T>) fluxResult.collect(Collectors.toSet());
            }
            throw new RuntimeException();

        }else{
            return ((Mono<?>) model).cache().map(m -> {
                try {
                    return visit(bomOrValue, m, reponseSchema, 1, false);
                } catch (InvocationTargetException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private <T> T visit(BomOrValue bomOrValue, Object parentModel, Schema<T> current, int startIdx, boolean useActualType) throws InvocationTargetException, IllegalAccessException {
        Object currentModel = getModelByPath(current.getPath(), parentModel, startIdx);
        switch (bomOrValue.getType()){
            case BOM : return visitBom(bomOrValue, currentModel, current, useActualType);
            case VALUE : return visitValue(bomOrValue, currentModel, current);
            default:
                throw new RuntimeException();
        }
    }

    private <T> T visitValue(BomOrValue bomOrValue, Object currentModel, Schema<T> current){
        ValueHandler<T> valueHandler = current.getValueHandler();
        return null != valueHandler ? valueHandler.apply(currentModel, bomOrValue.value()) : (T) currentModel;
    }

    private <T> T visitBom(BomOrValue bomOrValue, Object currentModel, Schema<?> current, boolean useActualType) throws InvocationTargetException, IllegalAccessException {
        if(!current.isResponseCollection() || useActualType){
            return (T) writeObject(bomOrValue.bom(), currentModel, current, current.getActualType());
        }

        Stream<?> stream = toStream(currentModel)
                .map(m -> {
                    try {
                        return writeObject(bomOrValue.bom(), m, current, current.getActualType());
                    } catch (InvocationTargetException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });

        if (current.getResponseType().isArray()) {
            return (T) stream.toArray(i -> (T[]) Array.newInstance(current.getActualType(), i));
        }

        if (List.class.isAssignableFrom(current.getResponseType())) {
            return (T) stream.collect(Collectors.toList());
        }

        if (Set.class.isAssignableFrom(current.getResponseType())) {
            return (T) stream.collect(Collectors.toSet());
        }

        throw new RuntimeException();
    }

    private <T> T writeObject(Bom bom, Object model, Schema<?> current, Class<T> type) throws InvocationTargetException, IllegalAccessException {
        T result = BeanUtils.instantiateClass(type);
        for(Map.Entry<String, BomOrValue> entry : bom.entrySet()){
            Schema<?> childSchema = current.getChildren().get(entry.getKey());
            BomOrValue child = entry.getValue();
            if(null != childSchema){
                Object fieldValue = visit(child, model, childSchema, 0, false);
                if(null != fieldValue &&( childSchema.getResponseType().isPrimitive()
                    || fieldValue.getClass().isPrimitive() || childSchema.getResponseType().isAssignableFrom(fieldValue.getClass()))) {
                    childSchema.getWriteMethod().invoke(result, fieldValue);
                }
            }
        }
        return result;
    }

    private <T> Stream<T> toStream(Object model){
        if(model instanceof Collection<?>){
            return ((Collection<T>) model).stream();
        }

        return Arrays.stream((T[]) model);
    }

    private static final BiFunction<Object,String, ?> mapFunc = (model , p) -> {
        try {
            if(model instanceof byte[]){
                return  ((byte[])model)[Integer.valueOf(p)];
            }
            if(model instanceof short[]){
                return  ((short[])model)[Integer.valueOf(p)];
            }
            if(model instanceof int[]){
                return  ((int[])model)[Integer.valueOf(p)];
            }
            if(model instanceof long[]){
                return  ((long[])model)[Integer.valueOf(p)];
            }
            if(model instanceof float[]){
                return  ((float[])model)[Integer.valueOf(p)];
            }
            if(model instanceof double[]){
                return  ((double[])model)[Integer.valueOf(p)];
            }
            if(model instanceof boolean[]){
                return  ((boolean[])model)[Integer.valueOf(p)];
            }
            if(model instanceof char[]){
                return  ((char[])model)[Integer.valueOf(p)];
            }
            if(model instanceof Object[]){
                return  ((Object[])model)[Integer.valueOf(p)];
            }
            if(model instanceof List<?>){
                return  ((List<?>)model).get(Integer.valueOf(p));
            }
            if(model instanceof Map<?,?>){
                return ((Map<String, ?>)model).get(p);
            }

            PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(model.getClass(), p);
            if(null == pd || null == pd.getReadMethod()){
                return null;
            }
            return pd.getReadMethod().invoke(model);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    };

    private Object getModelByPath(List<String> path, Object parentModel, int startIdx) {
        Object currentModel = parentModel;
        for(int i = startIdx; i < path.size() && null != currentModel; i++){
            String p = path.get(i);
            currentModel = mapFunc.apply(currentModel, p);
        }
        return currentModel;
    }

    @Override
    public <T,U> Publisher<T> map(Publisher<Bom> targetBomPubliasher, Class<T> targetType, Class<U> modelType, Map<String, Publisher<?>> sourceModels) {
        Publisher<Bom> modelBomPublisher = Mono.from(targetBomPubliasher)
                .map(bom -> bomAdapter.transformBom(bom, targetType));
        Mono<U> modelResult = ((Mono<U>) map(modelBomPublisher, modelType, sourceModels)).cache();
        Map<String, Publisher<?>> targetModels = this.registrySchemaIfAbsent(modelType).getChildren()
                .entrySet()
                .stream()
                .filter(entry -> null != entry.getValue().getReadMeothd())
                .collect(Collectors.toMap(Map.Entry::getKey, entry->{
                    Schema<?> schema = entry.getValue();
                    return  modelResult.map(u -> {
                        try {
                            return schema.getReadMeothd().invoke(u);
                        } catch (IllegalAccessException | InvocationTargetException e ) {
                            throw new RuntimeException(e);
                        }
                    });
                }));
        return map(targetBomPubliasher, targetType, targetModels);
    }

    @Override
    public BomAdapter getBomAdapter(){
        return this.bomAdapter;
    }
}
