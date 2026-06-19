package io.github.hanbernate.jsonbom.example;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.spring.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.sql.Types;
import java.util.AbstractMap;
import java.util.Map;

@Slf4j
@Repository
public class UserRepository extends AbstractBomDao<User> {
    public Mono<User> findByUserId(Mono<Bom> bomPublisher, Mono<Long> userIdPublisher) {
        return bomPublisher.zipWith(userIdPublisher).flatMap(t -> {
            String sql = getSelectSql(t.getT1()) + " WHERE user_id = ?";
            return Mono.justOrEmpty(findOne(sql,
                new Map.Entry[]{new AbstractMap.SimpleEntry<>(t.getT2(), Types.BIGINT)},
                getRowDefaultMapper()));
        });
    }
}
