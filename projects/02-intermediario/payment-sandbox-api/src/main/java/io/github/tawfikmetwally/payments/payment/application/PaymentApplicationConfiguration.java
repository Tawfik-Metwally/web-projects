package io.github.tawfikmetwally.payments.payment.application;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.tawfikmetwally.payments.payment.domain.DeterministicPaymentSimulator;

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
