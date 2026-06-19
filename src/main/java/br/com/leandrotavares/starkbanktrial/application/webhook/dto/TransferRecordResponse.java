package br.com.leandrotavares.starkbanktrial.application.webhook.dto;

import java.time.Instant;

import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.TransferRecordEntity;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.TransferStatus;

public record TransferRecordResponse(
        String transferId,
        String invoiceId,
        String eventId,
        String externalId,
        Long grossAmount,
        Long feeAmount,
        Long netAmount,
        TransferStatus status,
        Instant createdAt,
        String errorMessage
) {

    public static TransferRecordResponse from(TransferRecordEntity transfer) {
        return new TransferRecordResponse(
                transfer.getStarkTransferId(),
                transfer.getInvoiceId(),
                transfer.getEventId(),
                transfer.getExternalId(),
                transfer.getGrossAmount(),
                transfer.getFeeAmount(),
                transfer.getNetAmount(),
                transfer.getStatus(),
                transfer.getCreatedAt(),
                transfer.getErrorMessage()
        );
    }
}
