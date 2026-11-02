package io.github.cptfahd.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.web.service.registry.ImportHttpServices;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;


@Testcontainers
class BackendApplicationTests {

	@ServiceConnection
	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

	@Test
	void contextLoads() {
		postgres.start();
	}

}
