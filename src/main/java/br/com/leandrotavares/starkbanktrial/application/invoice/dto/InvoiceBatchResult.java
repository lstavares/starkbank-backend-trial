package br.com.leandrotavares.starkbanktrial.application.invoice.dto;

import java.util.List;

import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.BatchTriggerSource;

public record InvoiceBatchResult(
        String batchId,
        int invoiceCount,
        String status,
        BatchTriggerSource triggerSource,
        Integer sequenceNumber,
        List<InvoiceResult> invoices,
        String message
) {

    public static InvoiceBatchResult skipped(BatchTriggerSource triggerSource, Integer sequenceNumber, String message) {
        return new InvoiceBatchResult(null, 0, "SKIPPED", triggerSource, sequenceNumber, List.of(), message);
    }
}
