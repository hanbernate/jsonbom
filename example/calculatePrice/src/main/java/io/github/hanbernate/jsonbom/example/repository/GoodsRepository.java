package io.github.hanbernate.jsonbom.example.repository;

import java.math.BigDecimal;

import org.springframework.stereotype.Repository;

import io.github.hanbernate.jsonbom.api.Bom;
import lombok.AllArgsConstructor;
import lombok.Data;
import reactor.core.publisher.Mono;

/**
 * In-process goods data source. The example keeps fixed data so it stays
 * self-contained; only the fields present in the sub-BOM are filled, mimicking a
 * selective column query. The real HTTP hop is demonstrated by the integration
 * test, which calls {@code PriceController} with a {@code WebClient}.
 */
@Repository
public class GoodsRepository {
    @Data
    @AllArgsConstructor
    public static class Goods {
        private Long goodsId;
        private String goodsName;
        private BigDecimal originalPrice;
        private BigDecimal discount;
    }

    public Mono<Goods> findById(Mono<Bom> bom, Mono<Long> goodsId) {
        return Mono.zip(bom, goodsId, (b, id) -> {
            Goods goods = new Goods(0L, "", BigDecimal.ZERO, BigDecimal.ZERO);
            if (b.containsKey("goodsId")) {
                goods.setGoodsId(id);
            }
            if (b.containsKey("goodsName")) {
                goods.setGoodsName("Sample Goods");
            }
            if (b.containsKey("originalPrice")) {
                goods.setOriginalPrice(new BigDecimal("199.00"));
            }
            if (b.containsKey("discount")) {
                goods.setDiscount(new BigDecimal("0.8"));
            }
            return goods;
        });
    }
}
