package io.github.hanbernate.jsonbom.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point of the {@code calculatePrice} example.
 * <p>
 * The scan root is raised to {@code io.github.hanbernate.jsonbom} so that both
 * the {@code example} package (orchestrator, controllers, repositories) and the
 * {@code core} package (the {@code PublisherLog} aspect) are picked up. The
 * previous {@code AppConfig} only scanned the {@code example} package, which is
 * why the aspect was never woven into a running context.
 */
@SpringBootApplication(scanBasePackages = "io.github.hanbernate.jsonbom")
public class PriceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PriceApplication.class, args);
    }
}
