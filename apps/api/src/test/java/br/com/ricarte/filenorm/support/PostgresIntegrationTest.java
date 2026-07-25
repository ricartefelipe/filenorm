package br.com.ricarte.filenorm.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        String url = System.getenv().getOrDefault(
                "FILENORM_TEST_DB_URL",
                "jdbc:postgresql://127.0.0.1:5433/filenorm"
        );
        String user = System.getenv().getOrDefault("FILENORM_TEST_DB_USER", "filenorm");
        String password = System.getenv().getOrDefault("FILENORM_TEST_DB_PASSWORD", "filenorm");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> password);
    }
}
