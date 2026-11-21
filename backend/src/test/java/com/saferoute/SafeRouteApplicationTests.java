package com.saferoute;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class SafeRouteApplicationTests {

    @Test
    void contextLoads() {
        // Verifies Spring context, Flyway migration, and JPA repositories load cleanly
    }
}
