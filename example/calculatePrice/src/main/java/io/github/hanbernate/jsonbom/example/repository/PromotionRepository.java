package io.github.hanbernate.jsonbom.example.repository;

import io.github.hanbernate.jsonbom.api.Bom;
import lombok.AllArgsConstructor;
import lombok.Data;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.stream.Collectors;
import java.util.List;

public class PromotionRepository {
    @Data
    @AllArgsConstructor
    public static class Promotion{
        private Long promotionId;
        private String promotionName;
        private BigDecimal discount;
    }

    public Mono<List<Promotion>> findByGoodsIdId(Mono<Bom> bom, Mono<Long> goodsId){
        Flux<Long> promotionIds = goodsId.flatMapMany(g -> {
            return Flux.fromArray(new Integer[]{1,2,3})
                .map(p -> g * 10000 + p);
        });
        return promotionIds.flatMap(promotionId -> {
            return bom.map(b ->{
                Promotion item = new Promotion(0L, "", BigDecimal.ZERO);
                if (b.containsKey("promotionId")) {
                    item.setPromotionId(promotionId);
                }
                if (b.containsKey("promotionName")) {
                    item.setPromotionName("Sample Promotion " + promotionId);
                }
                if (b.containsKey("discount")) {
                    item.setDiscount(new BigDecimal("0.8"));
                }
                return item;

            });
        }).collect(Collectors.toList());
    }

}
