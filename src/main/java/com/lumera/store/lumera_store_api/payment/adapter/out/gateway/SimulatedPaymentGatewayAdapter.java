package com.lumera.store.lumera_store_api.payment.adapter.out.gateway;

import com.lumera.store.lumera_store_api.payment.application.port.out.PaymentDecision;
import com.lumera.store.lumera_store_api.payment.application.port.out.PaymentGatewayPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
public class SimulatedPaymentGatewayAdapter implements PaymentGatewayPort {

    private final PaymentDecision paymentDecision;

    public SimulatedPaymentGatewayAdapter(
            @Value("${app.payment.simulator.decision:APPROVED}")
            PaymentDecision paymentDecision) {
        this.paymentDecision = paymentDecision;
    }


    @Override
    public PaymentDecision charge(UUID paymentId, UUID orderId, BigDecimal amount) {
        log.debug(
                "Simulated payment: paymentId={}, orderId={}, decision={}",
                paymentId,
                orderId,
                paymentDecision
        );

        return paymentDecision;
    }
}
