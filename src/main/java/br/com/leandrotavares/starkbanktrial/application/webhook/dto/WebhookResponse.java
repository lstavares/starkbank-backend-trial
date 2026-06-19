package br.com.leandrotavares.starkbanktrial.application.webhook.dto;

public record WebhookResponse(
        String status,
        String eventId,
        String invoiceId,
        String transferId,
        String message
) {
}
