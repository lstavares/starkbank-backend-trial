package br.com.leandrotavares.starkbanktrial.application.invoice.dto;

import java.time.Instant;

import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.InvoiceRecordEntity;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.InvoiceStatus;

public record InvoiceResult(
        String starkInvoiceId,
        String batchId,
        Long amount,
        String name,
        String taxId,
        InvoiceStatus status,
        Long feeAmount,
        Instant createdAt
) {

    public static InvoiceResult from(InvoiceRecordEntity invoice) {
        return new InvoiceResult(
                invoice.getStarkInvoiceId(),
                invoice.getBatchId(),
                invoice.getAmount(),
                invoice.getName(),
                invoice.getTaxId(),
                invoice.getStatus(),
                invoice.getFeeAmount(),
                invoice.getCreatedAt()
        );
    }
}
