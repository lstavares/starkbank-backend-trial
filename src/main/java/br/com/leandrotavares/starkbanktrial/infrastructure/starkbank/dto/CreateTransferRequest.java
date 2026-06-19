package br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto;

import java.util.List;

public record CreateTransferRequest(
        Long amount,
        String bankCode,
        String branchCode,
        String accountNumber,
        String accountType,
        String taxId,
        String name,
        String externalId,
        List<String> tags
) {
}
