package io.github.hanbernate.jsonbom.example.repository;

import io.github.hanbernate.jsonbom.api.Bom;
import lombok.AllArgsConstructor;
import lombok.Data;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public class GoodsRepository {
    @Data
    @AllArgsConstructor
    public static class Goods{
        private Long goodsId;
        private String goodsName;
        private BigDecimal originalPrice;
        private BigDecimal discount;
    }
    public Mono<Goods> findById(Mono<Bom> bom, Mono<Long> goodsId){
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
