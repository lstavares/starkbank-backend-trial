package br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto;

public record CreatedTransferResult(
        String id,
        String externalId,
        Long amount,
        Long fee,
        String status
) {
}
