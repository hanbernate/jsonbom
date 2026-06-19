package io.github.hanbernate.jsonbom.example;

import java.time.LocalDateTime;
import lombok.Data;
import io.github.hanbernate.jsonbom.api.BomMapping;

@Data
public class OrderLogResponse{
    String before;
    String after;
    @BomMapping("createdAt")
    LocalDateTime time;
}
