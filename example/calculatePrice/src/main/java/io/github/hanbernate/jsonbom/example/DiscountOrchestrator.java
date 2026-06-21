package io.github.hanbernate.jsonbom.example;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.BomMapping;
import io.github.hanbernate.jsonbom.api.BomOrValue;
import io.github.hanbernate.jsonbom.api.ValueHandler;
import io.github.hanbernate.jsonbom.example.repository.PromotionRepository;
import io.github.hanbernate.jsonbom.example.repository.PromotionRepository.Promotion;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import reactor.core.publisher.Mono;

public class DiscountOrchestrator {
    PromotionRepository promotionRepository;

    public Mono<BigDecimal> calculateDiscount(Mono<Long> goodsId){
            Bom promotion = new Bom();
            promotion.merge("discount", BomOrValue.EMPTY);
            return promotionRepository.findByGoodsIdId(Mono.just(promotion), goodsId)
                .map(l -> {
                    return l.stream()
                        .map(Promotion::getDiscount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                });
    }
}
