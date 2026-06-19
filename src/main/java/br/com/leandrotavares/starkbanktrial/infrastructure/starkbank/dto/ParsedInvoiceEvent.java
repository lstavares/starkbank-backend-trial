package br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto;

public record ParsedInvoiceEvent(
        String eventId,
        String subscription,
        String invoiceId,
        String invoiceLogId,
        String logType,
        String status,
        Long amount,
        Long fee,
        boolean feeKnown
) {
}
