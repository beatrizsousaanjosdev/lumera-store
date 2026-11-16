package com.lumera.store.lumera_store_api.notification.application.port.in;

import com.lumera.store.lumera_store_api.notification.application.command.NotifyOrderOutcomeCommand;
import com.lumera.store.lumera_store_api.notification.domain.OrderNotification;

public interface NotifyOrderOutcomeUsecase {
    OrderNotification execute(NotifyOrderOutcomeCommand command);
}
