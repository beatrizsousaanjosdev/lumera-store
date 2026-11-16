package com.lumera.store.lumera_store_api.payment.application.port.in;

import com.lumera.store.lumera_store_api.payment.application.command.ProcessPaymentCommand;
import com.lumera.store.lumera_store_api.payment.domain.Payment;

public interface ProcessPaymentUsecase {
    Payment execute(ProcessPaymentCommand command);
}
