package io.github.hanbernate.jsonbom.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.JsonBomMapper;
import io.github.hanbernate.jsonbom.example.PriceOrchestrator.PriceModel;
import io.github.hanbernate.jsonbom.example.PriceOrchestrator.PriceTextValueHandler;
import io.github.hanbernate.jsonbom.example.repository.GoodsRepository;
import io.github.hanbernate.jsonbom.jackson.JacksonDeserializer;
import io.github.hanbernate.jsonbom.jackson.JacksonNameParser;
import io.github.hanbernate.jsonbom.spring.ReactorJsonBomMapper;

import org.junit.jupiter.api.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("PriceOrchestrator tests")
class PriceOrchestratorTest {

    private PriceOrchestrator orchestrator;
    private JsonBomMapper jsonBomMapper;
    private ObjectMapper jsonMapper;

    @BeforeEach
    void setUp() throws Exception {
        ReactorJsonBomMapper mapper = new ReactorJsonBomMapper();
        mapper.setNameParser(new JacksonNameParser());
        jsonBomMapper = mapper;

        ObjectMapper objectMapper = new ObjectMapper();
        JsonDeserializer<Bom> deserializer = new JacksonDeserializer();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Bom.class, deserializer);
        objectMapper.registerModule(module);
        jsonMapper = objectMapper;

        orchestrator = new PriceOrchestrator();
        Field f = PriceOrchestrator.class.getDeclaredField("jsonBomMapper");
        f.setAccessible(true);
        f.set(orchestrator, jsonBomMapper);
    }

    // =========================================================================
    // 1. getPriceModel tests
    // =========================================================================

    @Nested
    @DisplayName("getPriceModel tests")
    class GetPriceModelTest {

        private GoodsRepository mockGoodsRepo;
        private DiscountOrchestrator mockDiscountOrch;

        @BeforeEach
        void setUp() throws Exception {
            mockGoodsRepo = mock(GoodsRepository.class);
            mockDiscountOrch = mock(DiscountOrchestrator.class);

            Field f = PriceOrchestrator.class.getDeclaredField("goodsRepository");
            f.setAccessible(true);
            f.set(orchestrator, mockGoodsRepo);

            f = PriceOrchestrator.class.getDeclaredField("discountOrchestrator");
            f.setAccessible(true);
            f.set(orchestrator, mockDiscountOrch);
        }

        @Test
        @DisplayName("getPriceModel returns price model with all fields when requested")
        void allFieldsRequested() throws Exception {
            GoodsRepository.Goods goods = new GoodsRepository.Goods(
                    42L, "Test Goods", new BigDecimal("199.00"), BigDecimal.ZERO);
            when(mockGoodsRepo.findById(any(), any())).thenReturn(Mono.just(goods));
            when(mockDiscountOrch.calculateDiscount(any())).thenReturn(Mono.just(new BigDecimal("20.00")));

            String bomJson = """
                    {
                        "originalPrice": "",
                        "discount": "",
                        "finalPrice": ""
                    }
                    """;
            Bom requestBom = jsonMapper.readValue(bomJson, Bom.class);

            Mono<PriceModel> result = orchestrator.getPriceModel(Mono.just(requestBom), Mono.just(42L));

            StepVerifier.create(result)
                    .assertNext(model -> {
                        assertEquals(0, new BigDecimal("199.00").compareTo(model.getOriginalPrice()),
                                "originalPrice should be 199.00");
                        assertEquals(0, new BigDecimal("20.00").compareTo(model.getDiscount()),
                                "discount should be 20.00");
                        assertEquals(0, new BigDecimal("179.00").compareTo(model.getFinalPrice()),
                                "finalPrice should be 199.00 - 20.00 = 179.00");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("getPriceModel returns partial model when only some fields requested")
        void partialFieldsRequested() throws Exception {
            GoodsRepository.Goods goods = new GoodsRepository.Goods(
                    42L, "Test Goods", new BigDecimal("199.00"), BigDecimal.ZERO);
            when(mockGoodsRepo.findById(any(), any())).thenReturn(Mono.just(goods));
            when(mockDiscountOrch.calculateDiscount(any())).thenReturn(Mono.just(new BigDecimal("20.00")));

            String bomJson = """
                    {
                        "originalPrice": "",
                        "discount": ""
                    }
                    """;
            Bom requestBom = jsonMapper.readValue(bomJson, Bom.class);

            Mono<PriceModel> result = orchestrator.getPriceModel(Mono.just(requestBom), Mono.just(42L));

            StepVerifier.create(result)
                    .assertNext(model -> {
                        assertEquals(0, new BigDecimal("199.00").compareTo(model.getOriginalPrice()),
                                "originalPrice should be 199.00");
                        assertEquals(0, new BigDecimal("20.00").compareTo(model.getDiscount()),
                                "discount should be 20.00");
                        assertNull(model.getFinalPrice(), "finalPrice should be null when not requested");
                    })
                    .verifyComplete();
        }
    }

    // =========================================================================
    // 2. upstreamBom tests  — 7 combinations of {originalPrice, discount, finalPrice}
    // =========================================================================

    @Nested
    @DisplayName("upstreamBom tests")
    class UpstreamBomTest {

        private Bom invokeUpstreamBom(Bom input) throws Exception {
            Method method = PriceOrchestrator.class.getDeclaredMethod("upstreamBom", Bom.class);
            method.setAccessible(true);
            return (Bom) method.invoke(orchestrator, input);
        }

        @Test
        @DisplayName("1/7: only originalPrice → goods/originalPrice retained, no optimization")
        void onlyOriginalPrice() throws Exception {
            String bomJson = """
                    { "originalPrice": "" }
                    """;
            Bom requestBom = jsonMapper.readValue(bomJson, Bom.class);
            Bom upstream = invokeUpstreamBom(requestBom);

            // Should have "goods" → {"originalPrice": ""} and no discount or finalPrice
            assertTrue(upstream.containsKey("goods"), "should have goods key");
            Bom goodsBom = upstream.getBom("goods");
            assertNotNull(goodsBom, "goods should be a nested BOM");
            assertTrue(goodsBom.containsKey("originalPrice"), "goods should contain originalPrice");
            assertFalse(upstream.containsKey("discount"), "should NOT contain discount (optimization not triggered)");
            assertFalse(upstream.containsKey("finalPrice"), "should NOT contain finalPrice (not requested)");
        }

        @Test
        @DisplayName("2/7: only discount → discount retained, no optimization")
        void onlyDiscount() throws Exception {
            String bomJson = """
                    { "discount": "" }
                    """;
            Bom requestBom = jsonMapper.readValue(bomJson, Bom.class);
            Bom upstream = invokeUpstreamBom(requestBom);

            assertTrue(upstream.containsKey("discount"), "should have discount key");
            assertFalse(upstream.containsKey("goods"), "should NOT have goods (optimization not triggered)");
            assertFalse(upstream.containsKey("finalPrice"), "should NOT have finalPrice (not requested)");
        }

        @Test
        @DisplayName("3/7: only priceText (same as finalPrice) → optimization triggered")
        void onlyPriceText() throws Exception {
            // priceText maps to @BomMapping("finalPrice") → transform produces {"finalPrice":""}
            String bomJson = """
                    { "priceText": "" }
                    """;
            Bom requestBom = jsonMapper.readValue(bomJson, Bom.class);
            Bom upstream = invokeUpstreamBom(requestBom);

            assertTrue(upstream.containsKey("finalPrice"), "should have finalPrice (mapped from priceText)");
            assertTrue(upstream.containsKey("discount"), "should have discount added by optimization");
            assertTrue(upstream.containsKey("goods"), "should have goods added by optimization");
            Bom goodsBom = upstream.getBom("goods");
            assertTrue(goodsBom.containsKey("originalPrice"), "goods should contain originalPrice");
        }

        @Test
        @DisplayName("4/7: originalPrice + discount → both retained, no optimization")
        void originalPriceAndDiscount() throws Exception {
            String bomJson = """
                    {
                        "originalPrice": "",
                        "discount": ""
                    }
                    """;
            Bom requestBom = jsonMapper.readValue(bomJson, Bom.class);
            Bom upstream = invokeUpstreamBom(requestBom);

            assertTrue(upstream.containsKey("goods"), "should have goods key");
            assertTrue(upstream.getBom("goods").containsKey("originalPrice"), "goods should contain originalPrice");
            assertTrue(upstream.containsKey("discount"), "should have discount key");
            assertFalse(upstream.containsKey("finalPrice"), "should NOT contain finalPrice (not requested)");
        }

        @Test
        @DisplayName("5/7: originalPrice + finalPrice → optimization triggered")
        void originalPriceAndFinalPrice() throws Exception {
            String bomJson = """
                    {
                        "originalPrice": "",
                        "finalPrice": ""
                    }
                    """;
            Bom requestBom = jsonMapper.readValue(bomJson, Bom.class);
            Bom upstream = invokeUpstreamBom(requestBom);

            assertTrue(upstream.containsKey("finalPrice"), "should have finalPrice");
            assertTrue(upstream.containsKey("discount"), "should have discount added by optimization");
            assertTrue(upstream.containsKey("goods"), "should have goods key");
            Bom goodsBom = upstream.getBom("goods");
            assertTrue(goodsBom.containsKey("originalPrice"), "goods should contain originalPrice");
        }

        @Test
        @DisplayName("6/7: discount + finalPrice → optimization triggered")
        void discountAndFinalPrice() throws Exception {
            String bomJson = """
                    {
                        "discount": "",
                        "finalPrice": ""
                    }
                    """;
            Bom requestBom = jsonMapper.readValue(bomJson, Bom.class);
            Bom upstream = invokeUpstreamBom(requestBom);

            assertTrue(upstream.containsKey("finalPrice"), "should have finalPrice");
            assertTrue(upstream.containsKey("discount"), "should have discount key");
            assertTrue(upstream.containsKey("goods"), "should have goods added by optimization");
            Bom goodsBom = upstream.getBom("goods");
            assertTrue(goodsBom.containsKey("originalPrice"), "goods should contain originalPrice");
        }

        @Test
        @DisplayName("7/7: originalPrice + discount + finalPrice → all three requested, optimization triggered")
        void allThreeFields() throws Exception {
            String bomJson = """
                    {
                        "originalPrice": "",
                        "discount": "",
                        "finalPrice": ""
                    }
                    """;
            Bom requestBom = jsonMapper.readValue(bomJson, Bom.class);
            Bom upstream = invokeUpstreamBom(requestBom);

            assertTrue(upstream.containsKey("finalPrice"), "should have finalPrice");
            assertTrue(upstream.containsKey("discount"), "should have discount key");
            assertTrue(upstream.containsKey("goods"), "should have goods key");
            Bom goodsBom = upstream.getBom("goods");
            assertTrue(goodsBom.containsKey("originalPrice"), "goods should contain originalPrice");
        }
    }

    // =========================================================================
    // 3. calculateFinalPrice tests
    // =========================================================================

    @Nested
    @DisplayName("calculateFinalPrice tests")
    class CalculateFinalPriceTest {

        private PriceOrchestrator orchestrator;

        @BeforeEach
        void setUp() throws Exception {
            // calculateFinalPrice is an instance method on PriceOrchestrator
            // It only depends on the method parameters, not any fields.
            // A minimal orchestrator instance is sufficient.
            orchestrator = new PriceOrchestrator();
            // jsonBomMapper is not needed for this test, but inject a real one
            // so the instance is in a valid state.
            Field f = PriceOrchestrator.class.getDeclaredField("jsonBomMapper");
            f.setAccessible(true);
            f.set(orchestrator, jsonBomMapper);
        }

        private BigDecimal invokeCalculateFinalPrice(GoodsRepository.Goods g, BigDecimal d) throws Exception {
            Method method = PriceOrchestrator.class.getDeclaredMethod(
                    "calculateFinalPrice", GoodsRepository.Goods.class, BigDecimal.class);
            method.setAccessible(true);
            return (BigDecimal) method.invoke(orchestrator, g, d);
        }

        @Test
        @DisplayName("finalPrice with zero discount")
        void zeroDiscount() throws Exception {
            GoodsRepository.Goods goods = new GoodsRepository.Goods(
                    1L, "Item", new BigDecimal("100.00"), BigDecimal.ZERO);
            BigDecimal result = invokeCalculateFinalPrice(goods, BigDecimal.ZERO);
            assertEquals(0, new BigDecimal("100.00").compareTo(result),
                    "100.00 - 0 = 100.00");
        }

        @Test
        @DisplayName("finalPrice = originalPrice - discount")
        void normalDiscount() throws Exception {
            GoodsRepository.Goods goods = new GoodsRepository.Goods(
                    1L, "Item", new BigDecimal("100.00"), BigDecimal.ZERO);
            BigDecimal result = invokeCalculateFinalPrice(goods, new BigDecimal("30.00"));
            assertEquals(0, new BigDecimal("70.00").compareTo(result),
                    "100.00 - 30.00 = 70.00");
        }

        @Test
        @DisplayName("finalPrice with full discount (price becomes zero)")
        void fullDiscount() throws Exception {
            GoodsRepository.Goods goods = new GoodsRepository.Goods(
                    1L, "Item", new BigDecimal("100.00"), BigDecimal.ZERO);
            BigDecimal result = invokeCalculateFinalPrice(goods, new BigDecimal("100.00"));
            assertEquals(0, BigDecimal.ZERO.compareTo(result),
                    "100.00 - 100.00 = 0.00");
        }

        @Test
        @DisplayName("finalPrice with negative result (discount > originalPrice) is clamped to zero")
        void discountExceedsOriginalPrice() throws Exception {
            GoodsRepository.Goods goods = new GoodsRepository.Goods(
                    1L, "Item", new BigDecimal("50.00"), BigDecimal.ZERO);
            BigDecimal result = invokeCalculateFinalPrice(goods, new BigDecimal("80.00"));
            assertEquals(0, BigDecimal.ZERO.compareTo(result),
                    "50.00 - 80.00 < 0, should be clamped to 0.00");
        }
    }

    // =========================================================================
    // 4. PriceTextValueHandler tests
    // =========================================================================

    @Nested
    @DisplayName("PriceTextValueHandler tests")
    class PriceTextValueHandlerTest {

        private PriceTextValueHandler handler = new PriceTextValueHandler();

        @Test
        @DisplayName("handler prepends ￥ to model value")
        void prependsYenSymbol() {
            String result = handler.apply(new BigDecimal("99.00"), "");
            assertEquals("￥99.00", result);
        }

        @Test
        @DisplayName("handler works with integer values")
        void integerValues() {
            String result = handler.apply(new BigDecimal("100"), "");
            assertEquals("￥100", result);
        }

        @Test
        @DisplayName("handler returns null when model is null")
        void nullModel() {
            String result = handler.apply(null, "");
            assertNull(result);
        }

        @Test
        @DisplayName("handler ignores bomValue parameter")
        void ignoresBomValue() {
            String result = handler.apply(new BigDecimal("50.50"), "someBomValue");
            assertEquals("￥50.50", result);
        }
    }
}
