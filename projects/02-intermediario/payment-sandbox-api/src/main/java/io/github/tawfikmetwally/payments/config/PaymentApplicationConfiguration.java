package io.github.tawfikmetwally.payments.config;

import io.github.tawfikmetwally.payments.simulator.DeterministicPaymentSimulator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PaymentApplicationConfiguration {

    @Bean
    DeterministicPaymentSimulator deterministicPaymentSimulator() {
        return new DeterministicPaymentSimulator();
    }

    @Bean
    Clock paymentClock() {
        return Clock.systemUTC();
    }
}
