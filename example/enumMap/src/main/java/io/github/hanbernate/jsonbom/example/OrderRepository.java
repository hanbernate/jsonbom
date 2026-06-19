package io.github.hanbernate.jsonbom.example;

import org.springframework.stereotype.Repository;

import io.github.hanbernate.jsonbom.api.Bom;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import reactor.core.publisher.Mono;

@Repository
public class OrderRepository {

    @Data
    public static class Order {
        private Long id;
        private String detail;
        private BigDecimal price;
        private Integer status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    public Mono<Order> findById(Mono<Bom> bom, Mono<Long> orderId) {
        return bom.zipWith(orderId, (b, id) -> {
            Order result = new Order();
            if (b.containsKey("id")) {
                result.setId(id);
            }
            if (b.containsKey("detail")) {
                result.setDetail("detail msg");
            }
            if (b.containsKey("status")) {
                result.setStatus(3);
            }
            if (b.containsKey("price")) {
                result.setPrice(new BigDecimal("123"));
            }
            if (b.containsKey("createdAt")) {
                result.setCreatedAt(LocalDateTime.now());
            }
            if (b.containsKey("updatedAt")) {
                result.setUpdatedAt(LocalDateTime.now());
            }
            return result;
        });
    }
}
