package io.github.hanbernate.jsonbom.example;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.BomMapping;
import io.github.hanbernate.jsonbom.api.BomOrValue;
import io.github.hanbernate.jsonbom.api.JsonBomMapper;
import io.github.hanbernate.jsonbom.api.ValueHandler;
import io.github.hanbernate.jsonbom.example.repository.GoodsRepository;
import lombok.Data;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class PriceOrchestrator {
    GoodsRepository goodsRepository;

    DiscountOrchestrator discountOrchestrator;

    JsonBomMapper jsonBomMapper;

    @SuppressWarnings("unchecked")
    public Mono<PriceModel> getPriceModel(Mono<Bom> bom, Mono<Long> goodsId){
        Mono<Bom> upstreamBom = bom.map(this::upstreamBom).cache();

        Mono<GoodsRepository.Goods> goods = goodsRepository.findById(upstreamBom.map(b -> b.getBom("goods")), goodsId);
        Mono<BigDecimal> discount = discountOrchestrator.calculateDiscount(goodsId);
        Mono<BigDecimal> finalPrice = goods.zipWith(discount, (g, d) -> {
            return g.getOriginalPrice().subtract(d);
        });

        Map<String, Publisher<?>> models = new HashMap<>();
        models.put("goods", goods);
        models.put("discount", discount);
        models.put("finalPrice", finalPrice);
        return (Mono<PriceModel>) (Publisher<?>) jsonBomMapper.map(bom, PriceModel.class, models);
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

    @Data
    public static class PriceModel {
        @BomMapping("goods/originalPrice")
        private BigDecimal originalPrice;   //原价
        @BomMapping("discount")
        private BigDecimal discount;    //折扣
        @BomMapping("finalPrice")
        BigDecimal finalPrice;      //卖价
        @BomMapping(value="finalPrice", valueHandler = PriceTextValueHander.class)
        String priceText;       //价格文案
    }
    
    public static class PriceTextValueHander implements ValueHandler<String> {

        @Override
        public String apply(Object model, String bomValue) {
            if(null == model){
                return null;
            }
            return "￥" + model.toString();
        }
    
        
    }
}
