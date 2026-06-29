package io.github.hanbernate.jsonbom.core;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Aspect
@Component
public class PublisherLogAdvice {
    @Autowired
    ObjectMapper objectMapper;

    @Around("@annotation(PublisherLog)")
    public Object cachePublisherArgs(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = Arrays.stream(joinPoint.getArgs())
            .map(this::cachePublisher)
            .toArray(Object[]::new);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("class", joinPoint.getSignature().getDeclaringTypeName());
        root.put("method", joinPoint.getSignature().getName());

        String[] argNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
        Mono<ObjectNode> argNode = Mono.just(root.putObject("args"));
        for(int i = 0; i < argNames.length; i++){
            Mono<Optional<?>> argMono = safe2MonoOptional(args[i]);
            argNode = argNode.zipWith(argMono, (node, opt) ->{
                node.set(argNames[0], objectMapper.valueToTree(opt.orElse(null)));
                return node;
            });
        }

        Object result = joinPoint.proceed(args);
        if (result instanceof Publisher) {
            Object cacheResult = cachePublisher(result);
            Mono<Optional<?>> resultMonoOpational = safe2MonoOptional(cacheResult);
            argNode.zipWith(resultMonoOpational, (a, r) ->{
                log(root, r);
                return root;
            }).subscribe();
            return cacheResult;
        }

        argNode.doOnNext(r -> log(root, result)).subscribe();
        return result;
    }

    private Object cachePublisher(Object arg) {
        if (arg instanceof Mono) {
            return ((Mono<?>) arg).cache();
        } else if (arg instanceof Flux) {
            return ((Flux<?>) arg).cache();
        }
        return arg;
    }

    private Mono<Optional<?>> safe2MonoOptional(Object arg){
        if (arg instanceof Mono) {
            Mono<Optional<?>> r = ((Mono<?>) arg).map(Optional::ofNullable);
            return r.defaultIfEmpty(Optional.empty());
        } else if (arg instanceof Flux) {
            Mono<Optional<?>> r =  ((Flux<?>) arg).collect(Collectors.toList()).map(Optional::ofNullable);
            return r.defaultIfEmpty(Optional.empty());
        }
        return Mono.just(Optional.ofNullable(arg));
    }

    private void log(ObjectNode root, Object result){
        Object r = result instanceof Optional ? ((Optional<?>) result).orElse(null) : result;
        root.set("result", objectMapper.valueToTree(r));
        try{            
            String logString = objectMapper.writeValueAsString(root);
            log.debug(logString);
        }catch(Exception e){
            log.error(e.getMessage(), e);
        }
    }

}
