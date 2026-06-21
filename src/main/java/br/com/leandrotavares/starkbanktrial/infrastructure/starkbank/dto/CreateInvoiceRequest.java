package br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto;

import java.util.List;

public record CreateInvoiceRequest(
        Long amount,
        String name,
        String taxId,
        List<String> tags,
        List<InvoiceDescriptionRequest> descriptions,
        String due,
        Long expiration
) {
    public CreateInvoiceRequest(
            Long amount,
            String name,
            String taxId,
            List<String> tags,
            List<InvoiceDescriptionRequest> descriptions
    ) {
        this(amount, name, taxId, tags, descriptions, null, null);
    }
}
