package br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto;

public record InvoicePaymentDetails(
        String invoiceId,
        String status,
        Long invoiceAmount,
        Long invoiceFee,
        boolean invoiceFeeKnown,
        Long paymentAmount,
        String paymentMethod,
        String endToEndId
) {
}
