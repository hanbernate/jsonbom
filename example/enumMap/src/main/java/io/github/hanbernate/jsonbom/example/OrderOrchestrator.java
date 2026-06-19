package io.github.hanbernate.jsonbom.example;

import reactor.core.publisher.Mono;
import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.BomEnumModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.Setter;

@Service
public class OrderOrchestrator {
    
    @Autowired
    @Setter
    OrderRepository orderRepository;
    
    @Autowired
    @Setter
    OrderLogRepository orderLogRepository;

    public BomEnumModel<OrderModelFieldEnum> findById(Mono<Bom> bom, Mono<Long> orderId){
        BomEnumModel<OrderModelFieldEnum> models = new BomEnumModel<>();
        models.fillModel(OrderModelFieldEnum.ORDER, bom, b -> orderRepository.findById(b, orderId));
        models.fillModel(OrderModelFieldEnum.ORDER_LOG, bom, b -> orderLogRepository.findByOrderId(b, orderId).collectList());
        return models;
    }
}
