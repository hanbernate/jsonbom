package io.github.hanbernate.jsonbom.example;

import jakarta.persistence.Table;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Table(name = "t_user")
public class User {
    private Long id;
    private Long userId;
    private String name;
    private String avatar;
    private Long roleId;
    private String password;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
