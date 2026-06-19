package br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto;

public record CreatedInvoiceResult(
        String id,
        Long amount,
        Long fee,
        String status
) {
}
