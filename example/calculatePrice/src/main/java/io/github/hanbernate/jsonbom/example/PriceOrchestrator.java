package io.github.hanbernate.jsonbom.example;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.BomMapping;
import io.github.hanbernate.jsonbom.api.BomOrValue;
import io.github.hanbernate.jsonbom.api.JsonBomMapper;
import io.github.hanbernate.jsonbom.api.Type;
import io.github.hanbernate.jsonbom.api.ValueHandler;
import io.github.hanbernate.jsonbom.core.PublisherLog;
import io.github.hanbernate.jsonbom.example.repository.GoodsRepository;
import lombok.Data;

import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
public class PriceOrchestrator {
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    DiscountOrchestrator discountOrchestrator;
    @Autowired
    JsonBomMapper jsonBomMapper;

    @SuppressWarnings("unchecked")
    @PublisherLog
    public Mono<PriceModel> getPriceModel(Mono<Bom> bom, Mono<Long> goodsId){
        Mono<Bom> upstreamBom = bom.map(this::upstreamBom).cache();

        // Split the transformed BOM into the sub-BOM each upstream understands and
        // hand it over directly: no flatMap, no nested lambdas. The upstream BOM
        // already carries exactly the keys the response needs, so gating is handled
        // where it belongs — the mapper only subscribes a requested key, and each
        // repository only fills the fields present in its sub-BOM. Caching keeps the
        // goods publisher shared by originalPrice and finalPrice from being fetched
        // twice.
        Mono<Bom> goodsBom = subBom(upstreamBom, "goods");
        Mono<Bom> discountBom = subBom(upstreamBom, "discount");

        Mono<GoodsRepository.Goods> goods = goodsRepository.findById(goodsBom, goodsId).cache();
        Mono<BigDecimal> discount = discountOrchestrator.calculateDiscount(discountBom, goodsId).cache();
        Mono<BigDecimal> finalPrice = goods.zipWith(discount, this::calculateFinalPrice);

        Map<String, Publisher<?>> models = new HashMap<>();
        models.put("goods", goods);
        models.put("discount", discount);
        models.put("finalPrice", finalPrice);
        return (Mono<PriceModel>) (Publisher<?>) jsonBomMapper.map(bom, PriceModel.class, models);
    }

    private BigDecimal calculateFinalPrice(GoodsRepository.Goods g, BigDecimal d) {
        return g.getOriginalPrice().subtract(d).max(BigDecimal.ZERO);
    }

    private Bom upstreamBom(Bom targetBom){
        Bom r = jsonBomMapper.getBomAdapter().transformBom(targetBom, PriceModel.class);
        if(r.containsKey("finalPrice")){
            //折扣独立查询，不再查询商品
            r.merge("discount", BomOrValue.EMPTY);

            Bom goodsBom = new Bom();
            //商品只需要查原价
            goodsBom.merge("originalPrice", BomOrValue.EMPTY);
            r.merge("goods", new BomOrValue(null , goodsBom));
        }
        return r;
    }

    /**
     * Extracts the sub-BOM the downstream understands for {@code key} of the
     * transformed BOM:
     * <ul>
     *     <li>a nested node yields its own BOM;</li>
     *     <li>a leaf marker (such as {@code discount}) yields a BOM that still
     *         carries that key, so downstream {@code containsKey(...)} checks keep
     *         working;</li>
     *     <li>an absent key yields an empty {@code Mono}, so the downstream is
     *         skipped entirely.</li>
     * </ul>
     * Handing these sub-BOMs straight to the repositories avoids the nested
     * {@code flatMap} gating the orchestrator used to need.
     */
    private static Mono<Bom> subBom(Mono<Bom> upstreamBom, String key) {
        return upstreamBom.filter(b -> b.containsKey(key))
            .map(b -> {
                BomOrValue node = b.get(key);
                if (Type.BOM == node.getType() && null != node.bom()) {
                    return node.bom();
                }
                return Bom.createWithEmptyValue(key);
            });
    }

    @Data
    public static class PriceModel {
        @BomMapping("goods/originalPrice")
        private BigDecimal originalPrice;   //原价
        @BomMapping("discount")
        private BigDecimal discount;    //折扣
        @BomMapping("finalPrice")
        BigDecimal finalPrice;      //卖价
        @BomMapping(value="finalPrice", valueHandler = PriceTextValueHandler.class)
        String priceText;       //价格文案
    }
    
    public static class PriceTextValueHandler implements ValueHandler<String> {

        @Override
        public String apply(Object model, String bomValue) {
            if(null == model){
                return null;
            }
            return "￥" + model.toString();
        }
    
        
    }
}
