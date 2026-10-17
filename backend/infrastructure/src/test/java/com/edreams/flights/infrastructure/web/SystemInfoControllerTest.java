package com.edreams.flights.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.edreams.flights.infrastructure.config.ProvidersProperties;

@WebMvcTest(SystemInfoController.class)
@EnableConfigurationProperties(ProvidersProperties.class)
@TestPropertySource(properties = {
        "spring.application.name=flights-backend",
        "providers.alpha.base-url=http://alpha",
        "providers.alpha.timeout=2s",
        "providers.beta.base-url=http://beta",
        "providers.gamma.base-url=http://gamma"
})
class SystemInfoControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void exposesConfiguredProvidersInStableOrder() throws Exception {
        mvc.perform(get("/api/system/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("flights-backend"))
                .andExpect(jsonPath("$.version").value("dev"))
                .andExpect(jsonPath("$.providers.length()").value(3))
                .andExpect(jsonPath("$.providers[0].id").value("ALPHA"))
                .andExpect(jsonPath("$.providers[0].timeoutMillis").value(2000))
                .andExpect(jsonPath("$.providers[1].id").value("BETA"))
                .andExpect(jsonPath("$.providers[1].timeoutMillis").value(3000))
                .andExpect(jsonPath("$.providers[2].baseUrl").value("http://gamma"));
    }
}
