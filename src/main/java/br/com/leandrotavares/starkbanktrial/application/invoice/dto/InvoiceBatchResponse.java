package br.com.leandrotavares.starkbanktrial.application.invoice.dto;

import java.time.Instant;

import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.InvoiceBatchEntity;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.BatchStatus;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.BatchTriggerSource;

public record InvoiceBatchResponse(
        String batchId,
        BatchTriggerSource triggerSource,
        Integer sequenceNumber,
        int invoiceCount,
        BatchStatus status,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage
) {

    public static InvoiceBatchResponse from(InvoiceBatchEntity batch) {
        return new InvoiceBatchResponse(
                batch.getBatchId(),
                batch.getTriggerSource(),
                batch.getSequenceNumber(),
                batch.getInvoiceCount(),
                batch.getStatus(),
                batch.getStartedAt(),
                batch.getFinishedAt(),
                batch.getErrorMessage()
        );
    }
}
