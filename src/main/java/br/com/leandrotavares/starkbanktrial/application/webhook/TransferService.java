package br.com.leandrotavares.starkbanktrial.application.webhook;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.leandrotavares.starkbanktrial.application.webhook.dto.TransferCreationResult;
import br.com.leandrotavares.starkbanktrial.config.TransferDestinationProperties;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.TransferRecordEntity;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.repository.TransferRecordRepository;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.StarkBankTransferClient;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.CreateTransferRequest;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.CreatedTransferResult;

@Service
public class TransferService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final TransferRecordRepository transferRecordRepository;
    private final StarkBankTransferClient transferClient;
    private final TransferDestinationProperties destinationProperties;
    private final TransactionTemplate transactionTemplate;

    public TransferService(
            TransferRecordRepository transferRecordRepository,
            StarkBankTransferClient transferClient,
            TransferDestinationProperties destinationProperties,
            TransactionTemplate transactionTemplate
    ) {
        this.transferRecordRepository = transferRecordRepository;
        this.transferClient = transferClient;
        this.destinationProperties = destinationProperties;
        this.transactionTemplate = transactionTemplate;
    }

    public TransferCreationResult createTransfer(
            String eventId,
            String invoiceId,
            Long grossAmount,
            Long feeAmount,
            Long netAmount
    ) {
        String externalId = externalId(eventId);
        Optional<TransferRecordEntity> existingTransfer = findExistingTransfer(invoiceId, externalId);
        if (existingTransfer.isPresent()) {
            return skippedExistingTransfer(existingTransfer.get(), "Transfer already exists for this invoice or event.");
        }

        TransferRecordEntity transfer;
        try {
            transfer = createTransferRecord(invoiceId, eventId, externalId, grossAmount, feeAmount, netAmount);
        } catch (DataIntegrityViolationException exception) {
            return findExistingTransfer(invoiceId, externalId)
                    .map(existing -> skippedExistingTransfer(existing, "Transfer was created concurrently."))
                    .orElseThrow(() -> exception);
        }

        try {
            CreatedTransferResult createdTransfer = transferClient.createTransfer(toCreateTransferRequest(transfer));
            updateTransfer(externalId, entity -> entity.markSucceeded(createdTransfer.id()));
            return TransferCreationResult.succeeded(createdTransfer.id(), externalId);
        } catch (RuntimeException exception) {
            String errorMessage = summarizeError(exception);
            updateTransfer(externalId, entity -> entity.markFailed(errorMessage));
            return TransferCreationResult.failed(externalId, errorMessage);
        }
    }

    private TransferRecordEntity createTransferRecord(
            String invoiceId,
            String eventId,
            String externalId,
            Long grossAmount,
            Long feeAmount,
            Long netAmount
    ) {
        return transactionTemplate.execute(status -> transferRecordRepository.saveAndFlush(new TransferRecordEntity(
                invoiceId,
                eventId,
                externalId,
                grossAmount,
                feeAmount,
                netAmount,
                Instant.now()
        )));
    }

    private Optional<TransferRecordEntity> findExistingTransfer(String invoiceId, String externalId) {
        Optional<TransferRecordEntity> transferByInvoice = transferRecordRepository.findByInvoiceId(invoiceId);
        if (transferByInvoice.isPresent()) {
            return transferByInvoice;
        }
        return transferRecordRepository.findByExternalId(externalId);
    }

    private TransferCreationResult skippedExistingTransfer(TransferRecordEntity transfer, String message) {
        return TransferCreationResult.skipped(transfer.getStarkTransferId(), transfer.getExternalId(), message);
    }

    private TransferRecordEntity updateTransfer(String externalId, Consumer<TransferRecordEntity> update) {
        return transactionTemplate.execute(status -> {
            TransferRecordEntity transfer = transferRecordRepository.findByExternalId(externalId)
                    .orElseThrow(() -> new IllegalStateException("Transfer record not found: " + externalId));
            update.accept(transfer);
            return transferRecordRepository.saveAndFlush(transfer);
        });
    }

    private CreateTransferRequest toCreateTransferRequest(TransferRecordEntity transfer) {
        return new CreateTransferRequest(
                transfer.getNetAmount(),
                destinationProperties.getBankCode(),
                destinationProperties.getBranchCode(),
                destinationProperties.getAccountNumber(),
                destinationProperties.getAccountType(),
                destinationProperties.getTaxId(),
                destinationProperties.getName(),
                transfer.getExternalId(),
                List.of(
                        "trial",
                        "event:" + transfer.getEventId(),
                        "invoice:" + transfer.getInvoiceId()
                )
        );
    }

    private static String externalId(String eventId) {
        return "transfer-" + eventId;
    }

    private static String summarizeError(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }

        String summarized = message.replaceAll("\\s+", " ").trim();
        if (summarized.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return summarized;
        }
        return summarized.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
