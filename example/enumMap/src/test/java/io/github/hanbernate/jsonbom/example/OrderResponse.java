package io.github.hanbernate.jsonbom.example;

import java.time.LocalDateTime;
import lombok.Data;
import io.github.hanbernate.jsonbom.api.BomMapping;
import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderResponse{
    @BomMapping("order/id")
    Long orderId;

    @BomMapping("order/detail")
    String detail;

    @BomMapping("order/status")
    Integer status;

    @BomMapping("order/price")
    BigDecimal price;

    @BomMapping("orderLog")
    List<OrderLogResponse> logs;
}
