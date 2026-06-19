package br.com.leandrotavares.starkbanktrial.application.webhook.dto;

import java.time.Instant;

import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.WebhookEventRecordEntity;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.WebhookProcessingStatus;

public record WebhookEventResponse(
        String eventId,
        String subscription,
        String invoiceId,
        String invoiceLogId,
        String logType,
        WebhookProcessingStatus status,
        String errorMessage,
        Instant receivedAt,
        Instant processedAt
) {

    public static WebhookEventResponse from(WebhookEventRecordEntity event) {
        return new WebhookEventResponse(
                event.getStarkEventId(),
                event.getSubscription(),
                event.getInvoiceId(),
                event.getInvoiceLogId(),
                event.getLogType(),
                event.getStatus(),
                event.getErrorMessage(),
                event.getReceivedAt(),
                event.getProcessedAt()
        );
    }
}
