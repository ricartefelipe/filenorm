package br.com.ricarte.filenorm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TotalRecallProductionConfigurationTest {

    @Test
    void configuresThePublicTotalRecallEndpointForPasswordLogin() throws IOException {
        String compose = Files.readString(Path.of("..", "..", "docker-compose.prod.yml"));

        assertThat(compose).contains("TOTALRECALL_BASE_URL: ${TOTALRECALL_BASE_URL:-https://54.94.163.136.sslip.io}");
        assertThat(compose).contains("TOTALRECALL_SYSTEM_SLUG: ${TOTALRECALL_SYSTEM_SLUG:-filenorm}");
    }
}
