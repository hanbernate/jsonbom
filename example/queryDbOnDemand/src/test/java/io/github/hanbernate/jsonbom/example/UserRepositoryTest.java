package io.github.hanbernate.jsonbom.example;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.BomOrValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserRepository query on demand test")
public class UserRepositoryTest {

    private UserRepository userRepository;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);

        jdbcTemplate.execute("DROP TABLE IF EXISTS t_user");
        jdbcTemplate.execute("CREATE TABLE t_user (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "user_id BIGINT, " +
                "name VARCHAR(255), " +
                "avatar VARCHAR(255), " +
                "role_id BIGINT, " +
                "password VARCHAR(255), " +
                "created_at TIMESTAMP, " +
                "updated_at TIMESTAMP)");

        jdbcTemplate.update("INSERT INTO t_user (user_id, name, avatar, role_id, password) VALUES (?, ?, ?, ?, ?)",
                1001L, "Alice", "avatar1.png", 1L, "pwd1");
        jdbcTemplate.update("INSERT INTO t_user (user_id, name, avatar, role_id, password) VALUES (?, ?, ?, ?, ?)",
                1002L, "Bob", "avatar2.png", 2L, "pwd2");

        userRepository = new UserRepository();
        userRepository.setJdbcTemplate(jdbcTemplate);
    }

    @Test
    @DisplayName("findByUserId existing user returns correct user data")
    void findByUserId_existingUser_shouldReturnUser() {
        Bom bom = new Bom();
        bom.put("userId", BomOrValue.EMPTY);
        bom.put("name", BomOrValue.EMPTY);
        bom.put("avatar", BomOrValue.EMPTY);

        Mono<User> result = userRepository.findByUserId(Mono.just(bom), Mono.just(1001L));

        StepVerifier.create(result)
                .assertNext(user -> {
                    assertEquals(1001L, user.getUserId());
                    assertEquals("Alice", user.getName());
                    assertEquals("avatar1.png", user.getAvatar());
                    assertNull(user.getRoleId());
                    assertNull(user.getPassword());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("findByUserId non-existing user returns empty Mono")
    void findByUserId_nonExistingUser_shouldReturnEmpty() {
        Bom bom = new Bom();
        bom.put("userId", BomOrValue.EMPTY);

        Mono<User> result = userRepository.findByUserId(Mono.just(bom), Mono.just(9999L));

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    @DisplayName("findByUserId selects only requested columns from Bom")
    void findByUserId_shouldSelectRequestedColumnsOnly() {
        Bom bom = new Bom();
        bom.put("name", BomOrValue.EMPTY);

        Mono<User> result = userRepository.findByUserId(Mono.just(bom), Mono.just(1002L));

        StepVerifier.create(result)
                .assertNext(user -> {
                    assertEquals("Bob", user.getName());
                    assertNull(user.getUserId());
                    assertNull(user.getAvatar());
                })
                .verifyComplete();
    }
}
