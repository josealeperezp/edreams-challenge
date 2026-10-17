package com.edreams.flights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** Boots the real application against disposable PostgreSQL and Redis containers. */
@AutoConfigureMockMvc
class FlightsApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MockMvc mvc;

    @Test
    void flywayCreatedTheInventorySchema() {
        List<String> tables = jdbc
                .sql("select table_name from information_schema.tables where table_schema = 'public'")
                .query(String.class)
                .list();

        assertThat(tables).contains("itinerary", "provider_offer", "ingestion_run");
        Integer migrations = jdbc.sql("select count(*) from flyway_schema_history where success").query(Integer.class).single();
        assertThat(migrations).isGreaterThanOrEqualTo(2);
    }

    @Test
    void healthIsUpIncludingDatabaseAndRedis() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("UP"));
    }

    @Test
    void systemInfoListsTheThreeProviders() throws Exception {
        mvc.perform(get("/api/system/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("flights-backend"))
                .andExpect(jsonPath("$.providers.length()").value(3));
    }
}
