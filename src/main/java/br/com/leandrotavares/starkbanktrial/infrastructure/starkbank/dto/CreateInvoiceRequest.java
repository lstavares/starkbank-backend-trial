package br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto;

import java.util.List;

public record CreateInvoiceRequest(
        Long amount,
        String name,
        String taxId,
        List<String> tags,
        List<InvoiceDescriptionRequest> descriptions
) {
}
