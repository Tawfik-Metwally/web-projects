package io.github.tawfikmetwally.payments;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentSandboxApiApplicationTests {

	@Autowired
	private HealthEndpoint healthEndpoint;

	@Test
	void contextLoads() {
	}

	@Test
	void applicationHealthIsUp() {
		assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.UP);
	}

}
