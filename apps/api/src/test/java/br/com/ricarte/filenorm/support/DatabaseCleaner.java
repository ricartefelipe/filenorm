package br.com.ricarte.filenorm.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clean() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE normalized_events, jobs, credit_ledger, api_keys,
                login_tokens, sessions, accounts
                CASCADE
                """);
    }
}
