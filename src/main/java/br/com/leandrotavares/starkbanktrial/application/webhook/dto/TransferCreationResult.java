package br.com.leandrotavares.starkbanktrial.application.webhook.dto;

public record TransferCreationResult(
        TransferCreationStatus status,
        String transferId,
        String externalId,
        String message
) {

    public static TransferCreationResult succeeded(String transferId, String externalId) {
        return new TransferCreationResult(
                TransferCreationStatus.SUCCEEDED,
                transferId,
                externalId,
                "Transfer created successfully."
        );
    }

    public static TransferCreationResult skipped(String transferId, String externalId, String message) {
        return new TransferCreationResult(TransferCreationStatus.SKIPPED, transferId, externalId, message);
    }

    public static TransferCreationResult failed(String externalId, String message) {
        return new TransferCreationResult(TransferCreationStatus.FAILED, null, externalId, message);
    }
}
