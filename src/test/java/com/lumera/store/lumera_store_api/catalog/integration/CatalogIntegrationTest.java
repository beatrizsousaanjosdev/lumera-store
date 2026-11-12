package com.lumera.store.lumera_store_api.catalog.integration;

import com.jayway.jsonpath.JsonPath;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.outbox.enabled=false", "app.inventory.kafka.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class CatalogIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("lumera_integration")
            .withUsername("integration")
            .withPassword("integration");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void clearIsolatedDatabase() {
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM products");
    }

    @Test
    void shouldApplyMigrationsAndValidateJpaMapping() {
        assertEquals(4, flyway.info().applied().length);
        assertEquals("4", flyway.info().current().getVersion().toString());
        assertEquals("NO", jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'products' AND column_name = 'category'
                """, String.class));
    }

    @Test
    void shouldUpgradeLegacyProductToOtherCategory() throws Exception {
        // A separate schema exercises the upgrade without touching the application tables.
        var migration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema("legacy_upgrade")
                .schemas("legacy_upgrade")
                .locations("classpath:db/migration")
                .target("1")
                .load();
        migration.migrate();
        UUID id = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.prepareStatement("""
                     INSERT INTO legacy_upgrade.products (id, name, price, active) VALUES (?, ?, ?, ?)
                     """)) {
            statement.setObject(1, id);
            statement.setString(2, "Legacy product");
            statement.setBigDecimal(3, new BigDecimal("25.00"));
            statement.setBoolean(4, false);
            statement.executeUpdate();
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema("legacy_upgrade")
                .schemas("legacy_upgrade")
                .locations("classpath:db/migration")
                .load().migrate();

        assertEquals("OTHER", jdbc.queryForObject(
                "SELECT category FROM legacy_upgrade.products WHERE id = ?", String.class, id));
        assertEquals(new BigDecimal("25.00"), jdbc.queryForObject(
                "SELECT price FROM legacy_upgrade.products WHERE id = ?", BigDecimal.class, id));
        assertEquals(false, jdbc.queryForObject(
                "SELECT active FROM legacy_upgrade.products WHERE id = ?", Boolean.class, id));
    }

    @Test
    void shouldCreateReadUpdateAndChangeStatusWithRealPersistence() throws Exception {
        UUID id = create("Keyboard", "249.90", "PERIPHERALS");
        assertEquals("PERIPHERALS", jdbc.queryForObject(
                "SELECT category FROM products WHERE id = ?", String.class, id));
        mockMvc.perform(get("/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Keyboard"))
                .andExpect(jsonPath("$.price").value(249.90));

        mockMvc.perform(put("/products/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Monitor", "999.90", "MONITORS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("MONITORS"));
        assertEquals("Monitor", jdbc.queryForObject(
                "SELECT name FROM products WHERE id = ?", String.class, id));

        for (boolean active : List.of(false, false, true)) {
            mockMvc.perform(patch("/products/{id}/active", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":" + active + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(active));
            assertEquals(active, jdbc.queryForObject(
                    "SELECT active FROM products WHERE id = ?", Boolean.class, id));
        }
    }

    @Test
    void shouldFilterByCategoryAndActiveInPostgres() throws Exception {
        create("Mouse", "10.00", "PERIPHERALS");
        UUID inactive = create("Keyboard", "20.00", "PERIPHERALS");
        create("Monitor", "30.00", "MONITORS");
        mockMvc.perform(patch("/products/{id}/active", inactive)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/products").param("category", "PERIPHERALS"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/products").param("active", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/products").param("active", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/products").param("category", "PERIPHERALS").param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(inactive.toString()));
        mockMvc.perform(get("/products").param("category", "MONITORS").param("active", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/products").param("category", "MONITORS").param("active", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void shouldPaginateWithStableOrderingAndTotals() throws Exception {
        UUID first = create("Same name", "10.00", "OTHER");
        UUID second = create("Same name", "10.00", "OTHER");
        create("Zebra", "10.00", "OTHER");
        // Compare with PostgreSQL UUID ordering, rather than Java's signed UUID comparison.
        var orderedIds = jdbc.queryForList(
                "SELECT id FROM products ORDER BY name, id", UUID.class);
        for (int page = 0; page < 3; page++) {
            mockMvc.perform(get("/products").param("page", Integer.toString(page)).param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(orderedIds.get(page).toString()))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(3));
        }
        assertNotEquals(first, second);
        mockMvc.perform(get("/products").param("page", "3").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void shouldRejectInvalidRequestsWithoutChangingStoredProduct() throws Exception {
        UUID id = create("Mouse", "10.00", "OTHER");
        mockMvc.perform(put("/products/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Changed", "-1.00", "MONITORS")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Invalid", "10.00", "UNKNOWN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/products/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));
        assertEquals("Mouse", jdbc.queryForObject(
                "SELECT name FROM products WHERE id = ?", String.class, id));
    }

    @Test
    void shouldEnforceCategoryPriceAndPrimaryKeyConstraints() throws Exception {
        UUID id = create("Mouse", "10.00", "OTHER");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE products SET category = NULL WHERE id = ?", id));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE products SET category = 'UNKNOWN' WHERE id = ?", id));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE products SET price = 0 WHERE id = ?", id));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO products SELECT * FROM products WHERE id = ?", id));
    }

    private UUID create(String name, String price, String category) throws Exception {
        var response = mockMvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, price, category)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private String body(String name, String price, String category) {
        return """
                {"name":"%s", "description":"Integration test", "price":%s, "category":"%s"}
                """.formatted(name, price, category);
    }
}
