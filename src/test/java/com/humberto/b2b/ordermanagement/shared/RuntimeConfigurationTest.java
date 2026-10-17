package com.humberto.b2b.ordermanagement.shared;

import com.humberto.b2b.ordermanagement.TestcontainersConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RuntimeConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AsyncTaskExecutor applicationTaskExecutor;

    @Test
    void exposesHealthAndProbes() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void doesNotExposeOtherActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().isNotFound());
    }

    @Test
    void runsTasksOnVirtualThreads() throws Exception {
        CompletableFuture<Boolean> virtual = new CompletableFuture<>();

        applicationTaskExecutor.execute(() -> virtual.complete(Thread.currentThread().isVirtual()));

        assertTrue(virtual.get());
    }

    @Test
    void configuresConnectionPool() {
        HikariDataSource hikari = (HikariDataSource) dataSource;

        assertEquals(20, hikari.getMaximumPoolSize());
        assertEquals(5, hikari.getMinimumIdle());
        assertEquals(3000, hikari.getConnectionTimeout());
    }
}
