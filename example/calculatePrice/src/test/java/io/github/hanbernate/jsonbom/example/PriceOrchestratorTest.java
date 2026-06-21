package io.github.hanbernate.jsonbom.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.JsonBomMapper;
import io.github.hanbernate.jsonbom.example.repository.GoodsRepository;
import io.github.hanbernate.jsonbom.example.repository.PromotionRepository;
import io.github.hanbernate.jsonbom.jackson.JacksonDeserializer;
import io.github.hanbernate.jsonbom.jackson.JacksonNameParser;
import io.github.hanbernate.jsonbom.spring.ReactorJsonBomMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

@DisplayName("PriceOrchestrator getPriceModel test")
public class PriceOrchestratorTest {

    private JsonBomMapper bomMapper;
    private ObjectMapper jsonMapper;
    private PriceOrchestrator orchestrator;

    public PriceOrchestratorTest() {
        ReactorJsonBomMapper mapper = new ReactorJsonBomMapper();
        mapper.setNameParser(new JacksonNameParser());
        this.bomMapper = mapper;

        ObjectMapper objectMapper = new ObjectMapper();
        JsonDeserializer<Bom> deserializer = new JacksonDeserializer();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Bom.class, deserializer);
        objectMapper.registerModule(module);
        this.jsonMapper = objectMapper;

        GoodsRepository goodsRepository = new GoodsRepository();
        PromotionRepository promotionRepository = new PromotionRepository();
        DiscountOrchestrator discountOrchestrator = new DiscountOrchestrator();
        discountOrchestrator.promotionRepository = promotionRepository;

        orchestrator = new PriceOrchestrator();
        orchestrator.goodsRepository = goodsRepository;
        orchestrator.discountOrchestrator = discountOrchestrator;
        orchestrator.jsonBomMapper = bomMapper;
    }

    @Test
    @DisplayName("getPriceModel returns price model with originalPrice and discount")
    public void getPriceModel_shouldReturnPriceWithOriginalPriceAndDiscount() throws Exception {
        String requestJson = """
                {
                    "finalPrice":"",
                    "priceText":""
                }
                """;
        Bom requestBom = jsonMapper.readValue(requestJson, Bom.class);

        PriceOrchestrator.PriceModel result = orchestrator.getPriceModel(Mono.just(requestBom), Mono.just(1L)).block();

        assertNotNull(result);
        assertNull(result.getOriginalPrice());
        assertNull(result.getDiscount());
        assertEquals(new BigDecimal("196.60"), result.getFinalPrice());
        assertEquals("￥196.60", result.getPriceText());
    }
}
