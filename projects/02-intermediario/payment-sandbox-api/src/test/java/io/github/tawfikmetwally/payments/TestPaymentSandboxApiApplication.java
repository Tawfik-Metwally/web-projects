package io.github.tawfikmetwally.payments;

import org.springframework.boot.SpringApplication;

public class TestPaymentSandboxApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(PaymentSandboxApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
