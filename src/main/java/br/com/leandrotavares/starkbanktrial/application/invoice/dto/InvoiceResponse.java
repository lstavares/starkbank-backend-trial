package br.com.leandrotavares.starkbanktrial.application.invoice.dto;

import java.time.Instant;

import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.InvoiceRecordEntity;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.InvoiceStatus;

public record InvoiceResponse(
        String starkInvoiceId,
        String batchId,
        Long amount,
        String name,
        String taxId,
        InvoiceStatus status,
        Long feeAmount,
        Instant createdAt,
        Instant updatedAt
) {

    public static InvoiceResponse from(InvoiceRecordEntity invoice) {
        return new InvoiceResponse(
                invoice.getStarkInvoiceId(),
                invoice.getBatchId(),
                invoice.getAmount(),
                invoice.getName(),
                invoice.getTaxId(),
                invoice.getStatus(),
                invoice.getFeeAmount(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt()
        );
    }
}
