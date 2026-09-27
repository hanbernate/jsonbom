package io.github.hanbernate.jsonbom.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end test over a real HTTP hop. The application is started on its fixed
 * port and a {@link WebClient} posts a compact BOM to {@code /price/{goodsId}}.
 * <p>
 * The repositories return fixed in-process data, so this test focuses on the
 * HTTP contract: the {@code PriceController} accepts a BOM as {@code text/plain}
 * and answers with exactly the requested fields — an unrequested field is
 * absent from the response because it was never subscribed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@DisplayName("Price endpoint end-to-end tests")
class PriceEndpointIntegrationTest {

    private static final String BASE_URL = "http://localhost:18082";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private WebClient client;

    @BeforeEach
    void setUp() {
        client = WebClient.create(BASE_URL);
    }

    @Test
    @DisplayName("full BOM returns every requested field over HTTP")
    void fullBomReturnsAllRequestedFields() throws Exception {
        JsonNode model = postPrice("{\"originalPrice\":\"\",\"discount\":\"\",\"finalPrice\":\"\"}");

        assertDecimal(model, "originalPrice", "199.00");
        assertDecimal(model, "discount", "2.40");
        assertDecimal(model, "finalPrice", "196.60");
        assertAbsent(model, "priceText");
    }

    @Test
    @DisplayName("partial BOM returns only the requested fields")
    void partialBomOmitsUnrequestedFields() throws Exception {
        JsonNode model = postPrice("{\"originalPrice\":\"\",\"discount\":\"\"}");

        assertDecimal(model, "originalPrice", "199.00");
        assertDecimal(model, "discount", "2.40");
        assertAbsent(model, "finalPrice");
        assertAbsent(model, "priceText");
    }

    @Test
    @DisplayName("requesting priceText triggers the finalPrice optimization and renders the currency text")
    void priceTextRequestRendersCurrencyText() throws Exception {
        JsonNode model = postPrice("{\"priceText\":\"\"}");

        assertNotNull(model.get("priceText"), "priceText should be present");
        assertEquals("￥196.60", model.get("priceText").asString(), "priceText should be rendered");
        assertAbsent(model, "finalPrice");
        assertAbsent(model, "originalPrice");
    }

    @Test
    @DisplayName("a BOM requesting no goods-backed field returns only the discount")
    void discountOnlySkipsGoodsFields() throws Exception {
        JsonNode model = postPrice("{\"discount\":\"\"}");

        assertDecimal(model, "discount", "2.40");
        assertAbsent(model, "originalPrice");
        assertAbsent(model, "finalPrice");
    }

    @Test
    @DisplayName("an empty BOM returns an empty response")
    void emptyBomReturnsEmptyResponse() throws Exception {
        JsonNode model = postPrice("");

        assertAbsent(model, "originalPrice");
        assertAbsent(model, "discount");
        assertAbsent(model, "finalPrice");
        assertAbsent(model, "priceText");
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private JsonNode postPrice(String bomJson) throws Exception {
        String body = client.post().uri("/price/42")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(bomJson)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return jsonMapper.readTree(body);
    }

    private static void assertDecimal(JsonNode model, String field, String expected) {
        JsonNode node = model.get(field);
        assertNotNull(node, field + " should be present");
        assertTrue(!node.isNull(), field + " should not be null");
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(node.asString())),
                field + " should be " + expected);
    }

    private static void assertAbsent(JsonNode model, String field) {
        JsonNode node = model.get(field);
        assertTrue(node == null || node.isNull(), field + " should be absent/null");
    }
}
