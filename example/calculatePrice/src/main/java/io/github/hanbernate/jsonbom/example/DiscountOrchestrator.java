package io.github.hanbernate.jsonbom.example;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.BomOrValue;
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

    @PublisherLog
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
