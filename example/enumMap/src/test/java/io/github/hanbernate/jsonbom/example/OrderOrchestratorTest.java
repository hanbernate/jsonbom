package io.github.hanbernate.jsonbom.example;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.BomEnumModel;
import io.github.hanbernate.jsonbom.api.JsonBomMapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.hanbernate.jsonbom.jackson.JacksonDeserializer;
import io.github.hanbernate.jsonbom.jackson.JacksonNameParser;
import io.github.hanbernate.jsonbom.spring.ReactorJsonBomMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OrderOrchestrator findById test")
public class OrderOrchestratorTest {

    private JsonBomMapper bomMapper;

    ObjectMapper jsonMapper;

    private OrderOrchestrator orchestrator;

    public OrderOrchestratorTest(){
        ReactorJsonBomMapper mapper = new ReactorJsonBomMapper();
        mapper.setNameParser(new JacksonNameParser());
        this.bomMapper = mapper;

        ObjectMapper objectMapper = new ObjectMapper();
        JsonDeserializer<Bom> deserializer = new JacksonDeserializer();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Bom.class, deserializer);
        objectMapper.registerModule(module);
        this.jsonMapper = objectMapper;
    }

    @BeforeEach
    void setUp() {
        OrderRepository orderRepository = new OrderRepository();
        OrderLogRepository orderLogRepository = new OrderLogRepository();

        orchestrator = new OrderOrchestrator();
        orchestrator.setOrderRepository(orderRepository);
        orchestrator.setOrderLogRepository(orderLogRepository);
    }

    @Test
    @DisplayName("findById returns order and orderLog models")
    public void findById_shouldReturnOrderAndOrderLogModels() throws Exception {
        String requestBomJson = """
                {
                    "orderId":"",
                    "detail":"",
                    "status":"",
                    "price":"",
                    "logs":{
                        "before":"",
                        "after":"",
                        "time":""
                    }
                }
                """;
        Bom requestBom = jsonMapper.readValue(requestBomJson, Bom.class);
        Bom bom = bomMapper.getBomAdapter().transformBom(requestBom, OrderResponse.class);

        BomEnumModel<OrderModelFieldEnum> models = orchestrator.findById(Mono.just(bom), Mono.just(42L));

        assertNotNull(models);
        assertNotNull(models.getModels());
        assertEquals(2, models.getModels().size());

        Mono<OrderResponse> mono = (Mono<OrderResponse>)bomMapper.map(Mono.just(requestBom), OrderResponse.class, models.getModels());
        OrderResponse order = mono.block();
        assertNotNull(order);
        assertEquals(42L, order.getOrderId());
        assertEquals("detail msg", order.getDetail());
        assertEquals(Integer.valueOf(3), order.getStatus());
        assertNotNull(order.getPrice());

        List<OrderLogResponse> logs = order.getLogs();
        assertEquals(3, logs.size());
        OrderLogResponse log0 = logs.get(0);
        assertNotNull(log0.getBefore());
        assertNotNull(log0.getAfter());
        assertNotNull(log0.getTime());
    }
}
