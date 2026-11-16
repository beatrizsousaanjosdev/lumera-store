package com.lumera.store.lumera_store_api.order.application.port.in;

import com.lumera.store.lumera_store_api.order.application.command.ApplyPaymentResultCommand;
import com.lumera.store.lumera_store_api.order.domain.Order;

public interface ApplyPaymentResultUsecase {

    Order execute(ApplyPaymentResultCommand command);
}
