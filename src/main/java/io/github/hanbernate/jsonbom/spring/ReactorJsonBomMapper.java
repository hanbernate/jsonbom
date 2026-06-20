package io.github.hanbernate.jsonbom.spring;

import io.github.hanbernate.jsonbom.api.*;
import org.reactivestreams.Publisher;
import org.springframework.beans.BeanUtils;
import org.springframework.util.ReflectionUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
/**
 * Reactive JSON BOM mapper implementation using Project Reactor and Spring Framework.
 * <p>
 * This mapper provides reactive streaming support for transforming BOM structures
 * into Java objects using Spring's BeanUtils and Reactor's reactive types.
 *
 * @author hanbernate
 * @since 0.0.1
 */
public class ReactorJsonBomMapper implements JsonBomMapper {

    private ValueHandlers valueHandlers;

    private SchemaFactory schemaFactory;

    private BomAdapter bomAdapter;

    /**
     * Constructs a new ReactorJsonBomMapper with default Spring-based dependencies.
     * <p>
     * Initializes:
     * <ul>
     *     <li>{@link SpringBeanUtil} for bean introspection</li>
     *     <li>{@link DefaultValueHandlersImpl} for value handler management</li>
     *     <li>{@link DefaultSchemaFactoryImpl} for schema creation</li>
     *     <li>{@link BomAdapter} for BOM transformation</li>
     * </ul>
     *
     * @since 0.0.1
     */
    public ReactorJsonBomMapper(){
        BeanUtil beanUtil = new SpringBeanUtil();
        this.valueHandlers = createValueHandlers(beanUtil);
        this.schemaFactory = createSchemaFactory(beanUtil, this.valueHandlers);
        this.bomAdapter = BomAdapter.init(schemaFactory);
    }

    private ValueHandlers createValueHandlers(BeanUtil beanUtil){
        DefaultValueHandlersImpl instance = new DefaultValueHandlersImpl();
        instance.setBeanUtil(beanUtil);
        return instance;
    }

    private SchemaFactory createSchemaFactory(BeanUtil beanUtil, ValueHandlers valueHandlers){
        DefaultSchemaFactoryImpl instance = new DefaultSchemaFactoryImpl();
        instance.setBeanUtil(beanUtil);
        instance.setValueHandlers(valueHandlers);
        return instance;
    }

    /**
     * Sets a custom name parser for converting Field objects to schema names.
     *
     * @param nameParser the name parser function
     *
     * @since 0.0.1
     */
    public void setNameParser(Function<Field, String> nameParser){
        this.schemaFactory.setNameParser(nameParser);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.0.1
     */
    @Override
    public <T> Schema<T> registerSchemaIfAbsent(Class<T> responseType) {
        return this.schemaFactory.getByType(responseType);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.0.1
     */
   @Override
    public ValueHandler<?> registerValueHandler(Class<?> type, ValueHandler<?> valueHandler) {
        return this.valueHandlers.register(type, valueHandler);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.0.1
     */
    @Override
    public <T> Publisher<T> map(Publisher<Bom> bomPublisher, final Class<T> responseType, Map<String, Publisher<?>> models) {
        return ((Mono<Bom>) bomPublisher)
                .flatMap(bom ->{
                    Schema<T> responseSchema = registerSchemaIfAbsent(responseType);
                    Mono<T> result = Mono.just(BeanUtils.instantiateClass(responseType));
                    for(Map.Entry<String, BomOrValue> entry : bom.entrySet()){
                        Schema<?> childSchema = responseSchema.getChildren().get(entry.getKey());
                        if(null == childSchema){
                            continue;
                        }
                        BomOrValue child = entry.getValue();
                        Mono<?> fieldPublisher = visit(models, child, childSchema);
                        result = nullableZip(result, fieldPublisher, (r, v) ->{
                            if(null != v){
                                try {
                                    childSchema.getWriteMethod().invoke(r, v);
                                } catch (IllegalAccessException | InvocationTargetException e) {
                                    throw new JsonBomException("Fail to write field for schema(" + childSchema.toString4Exception(schemaFactory.getSeparator()) + ") and value(" + v.toString() + ")", e);
                                }
                            }
                            return r;
                        });

                    }

                    return result;
                });
    }

    private static <T, U, R> Mono<R> nullableZip(Mono<T> monoT, Mono<U> monoU, BiFunction<T, U ,R> func){
        Mono<Optional<T>> wrappedT = monoT.map(Optional::of).defaultIfEmpty(Optional.empty());
        Mono<Optional<U>> wrappedU = monoU.map(Optional::of).defaultIfEmpty(Optional.empty());
        return Mono.zip(wrappedT, wrappedU, (optT, optU) -> func.apply(optT.orElse(null), optU.orElse(null)));
    }
    /**
     * {@inheritDoc}
     *
     * @since 0.0.2
     */
    @Override
    public <T> Publisher<T> map(Publisher<Bom> bomPublisher, final Class<T> responseType,  BomModel bomModel) {
        return map(bomPublisher, responseType, bomModel.getModels());
    }

    @SuppressWarnings("unchecked")
    private <T> Mono<T> visit(Map<String, Publisher<?>> models, BomOrValue bomOrValue, Schema<T> responseSchema){
        if(null == responseSchema){
            return Mono.empty();
        }

        String path = 0 == responseSchema.getPath().size() ? "" : responseSchema.getPath().get(0);
        Publisher<?> model = models.get(path);
        if(null == model){
            return Mono.empty();
        }
        if(model instanceof Flux<?>){
            Flux<?> fluxResult = ((Flux<?>) model).cache().map(m -> {
                return visit(bomOrValue, m, responseSchema, 1, true);
            });

            if (responseSchema.getResponseType().isArray()) {
                throw new JsonBomException("Flux cannot be converted to array");
            }

            if (List.class.isAssignableFrom(responseSchema.getResponseType())) {
                return (Mono<T>) fluxResult.collect(Collectors.toList());
            }

            if (Set.class.isAssignableFrom(responseSchema.getResponseType())) {
                return (Mono<T>) fluxResult.collect(Collectors.toSet());
            }
            throw new JsonBomException("Flux cannot be converted to " + responseSchema.getResponseType().getName());

        }else{
            return ((Mono<?>) model).cache().map(m -> {
                return visit(bomOrValue, m, responseSchema, 1, false);
            });
        }
    }

    private <T> T visit(BomOrValue bomOrValue, Object parentModel, Schema<T> current, int startIdx, boolean useActualType) throws JsonBomException {
        if(null == parentModel){
            return null;
        }
        Object currentModel = getModelByPath(current.getPath(), parentModel, startIdx);
        switch (bomOrValue.getType()){
            case BOM : return visitBom(bomOrValue, currentModel, current, useActualType);
            case VALUE : return visitValue(bomOrValue, currentModel, current);
            default:
                throw new JsonBomException("Unknown type:" + bomOrValue.getType());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T visitValue(BomOrValue bomOrValue, Object currentModel, Schema<T> current){
        ValueHandler<T> valueHandler = current.getValueHandler();
        return null != valueHandler ? valueHandler.apply(currentModel, bomOrValue.value()) : (T) currentModel;
    }

    @SuppressWarnings("unchecked")
    private <T> T visitBom(BomOrValue bomOrValue, Object currentModel, Schema<?> current, boolean useActualType) throws JsonBomException {
        if(!current.isResponseCollection() || useActualType){
            return (T) writeObject(bomOrValue.bom(), currentModel, current, current.getActualType());
        }

        Stream<?> stream = toStream(currentModel)
                .map(m -> {
                    return writeObject(bomOrValue.bom(), m, current, current.getActualType());
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

        throw new JsonBomException("Unknown responseType:" + current.getResponseType().getName());
    }

    private <T> T writeObject(Bom bom, Object model, Schema<?> current, Class<T> type) throws JsonBomException {
        T result = BeanUtils.instantiateClass(type);
        for(Map.Entry<String, BomOrValue> entry : bom.entrySet()){
            Schema<?> childSchema = current.getChildren().get(entry.getKey());
            BomOrValue child = entry.getValue();
            if(null != childSchema){
                Object fieldValue = visit(child, model, childSchema, 0, false);
                if(null != fieldValue &&( childSchema.getResponseType().isPrimitive()
                    || fieldValue.getClass().isPrimitive() || childSchema.getResponseType().isAssignableFrom(fieldValue.getClass()))) {
                    try{
                        childSchema.getWriteMethod().invoke(result, fieldValue);
                    }catch(IllegalAccessException | InvocationTargetException e){
                         throw new JsonBomException("Fail to write field for schema(" + childSchema.toString4Exception(schemaFactory.getSeparator()) + ") and value(" + fieldValue.toString() + ")", e);
                    }
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T> Stream<T> toStream(Object model){
        if(model instanceof Collection<?>){
            return ((Collection<T>) model).stream();
        }

        return Arrays.stream((T[]) model);
    }

    @SuppressWarnings("unchecked")
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
            throw new JsonBomException("Fail to read value for field"+ p + " for " + model.getClass().getName(), e);
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

    /**
     * {@inheritDoc}
     *
     * @since 0.0.1
     */
    @Override
    public <T,U> Publisher<T> map(Publisher<Bom> targetBomPublisher, Class<T> targetType, Class<U> modelType, Map<String, Publisher<?>> sourceModels) {
        Publisher<Bom> modelBomPublisher = Mono.from(targetBomPublisher)
                .map(bom -> bomAdapter.transformBom(bom, targetType));
        Mono<U> modelResult = ((Mono<U>) map(modelBomPublisher, modelType, sourceModels)).cache();
        Map<String, Publisher<?>> targetModels = this.registerSchemaIfAbsent(modelType).getChildren()
                .entrySet()
                .stream()
                .filter(entry -> null != entry.getValue().getReadMethod())
                .collect(Collectors.toMap(Map.Entry::getKey, entry->{
                    Schema<?> schema = entry.getValue();
                    return  modelResult.map(u -> {
                        try {
                            return schema.getReadMethod().invoke(u);
                        } catch (IllegalAccessException | InvocationTargetException e ) {
            throw new JsonBomException("Fail to read value for schema" + schema.toString4Exception(schemaFactory.getSeparator()) + " in " + u.getClass().getName(), e);
                        }
                    });
                }));
        return map(targetBomPublisher, targetType, targetModels);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.0.1
     */
    @Override
    public <T,U> Publisher<T> map(Publisher<Bom> targetBomPublisher, Class<T> targetType, Class<U> modelType, BomModel bomModel) {
        return map(targetBomPublisher, targetType, modelType, bomModel.getModels());
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.0.1
     */
    @Override
    public BomAdapter getBomAdapter(){
        return this.bomAdapter;
    }
}
