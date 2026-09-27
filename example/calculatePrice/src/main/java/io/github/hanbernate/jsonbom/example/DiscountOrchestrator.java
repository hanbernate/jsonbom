package io.github.hanbernate.jsonbom.example;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.core.PublisherLog;
import io.github.hanbernate.jsonbom.example.repository.PromotionRepository;
import io.github.hanbernate.jsonbom.example.repository.PromotionRepository.Promotion;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class DiscountOrchestrator {
    @Autowired
    PromotionRepository promotionRepository;

    /**
     * Sums the discounts of every promotion of the goods. The caller passes the
     * promotion sub-BOM, so this method only aggregates the fields that sub-BOM
     * requested instead of building its own.
     */
    @PublisherLog
    public Mono<BigDecimal> calculateDiscount(Mono<Bom> promotionBom, Mono<Long> goodsId){
        return promotionRepository.findByGoodsIdId(promotionBom, goodsId)
            .map(l -> l.stream()
                .map(Promotion::getDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
