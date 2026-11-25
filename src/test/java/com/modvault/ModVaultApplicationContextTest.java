package com.modvault;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies the Spring context wires up (controllers, interceptors, MyBatis mappers,
 * encryption startup validation, admin-token validation). Uses an in-memory H2 datasource
 * with Flyway disabled; schema is exercised against PostgreSQL by the integration phase.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:modvault;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "modvault.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "modvault.admin-token=0123456789012345678901234567890123456789"
})
class ModVaultApplicationContextTest {

    @Test
    void contextLoads() {
    }
}
