package io.github.hanbernate.jsonbom.example;

import org.springframework.stereotype.Repository;
import io.github.hanbernate.jsonbom.example.OrderRepository.Order;
import io.github.hanbernate.jsonbom.api.Bom;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class OrderLogRepository {

    @Data
    public static class OrderLog {
        private Long id;
        private String before;
        private String after;
        private LocalDateTime createdAt;
    }

    public Flux<OrderLog> findByOrderId(Mono<Bom> bom, Mono<Long> orderId) {
        return bom.flatMapMany(b ->
            Flux.just(1, 2, 3)
                .flatMap(status -> orderId.map(id -> createOrderLog(b, id, status)))
        );
    }

    private OrderLog createOrderLog(Bom b, Long id, int targetStatus) {
        OrderLog orderLog = new OrderLog();
        if (b.containsKey("id")) {
            orderLog.setId(1L);
        }
        Order order = new Order();
        order.setId(id);
        order.setDetail("detail msg");
        order.setPrice(new BigDecimal("123"));
        order.setStatus(targetStatus - 1);
        order.setCreatedAt(LocalDateTime.now().minus(3, java.time.temporal.ChronoUnit.MINUTES));
        order.setUpdatedAt(LocalDateTime.now().minus(2 - targetStatus, java.time.temporal.ChronoUnit.MINUTES));
        if (b.containsKey("before")) {
            orderLog.setBefore(order.toString());
        }
        if (b.containsKey("after")) {
            order.setStatus(targetStatus);
            order.setUpdatedAt(LocalDateTime.now().minus(3 - targetStatus, java.time.temporal.ChronoUnit.MINUTES));
            orderLog.setAfter(order.toString());
        }
        if (b.containsKey("createdAt")) {
            orderLog.setCreatedAt(LocalDateTime.now().minus(3 - targetStatus, java.time.temporal.ChronoUnit.MINUTES));
        }
        return orderLog;
    }
}
