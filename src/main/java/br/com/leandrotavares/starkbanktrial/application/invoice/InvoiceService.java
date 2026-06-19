package br.com.leandrotavares.starkbanktrial.application.invoice;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.leandrotavares.starkbanktrial.application.invoice.dto.InvoiceBatchResponse;
import br.com.leandrotavares.starkbanktrial.application.invoice.dto.InvoiceBatchResult;
import br.com.leandrotavares.starkbanktrial.application.invoice.dto.InvoiceResponse;
import br.com.leandrotavares.starkbanktrial.application.invoice.dto.InvoiceResult;
import br.com.leandrotavares.starkbanktrial.config.InvoiceIssuingProperties;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.InvoiceBatchEntity;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.InvoiceRecordEntity;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.BatchTriggerSource;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.InvoiceStatus;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.repository.InvoiceBatchRepository;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.repository.InvoiceRecordRepository;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.StarkBankInvoiceClient;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.CreateInvoiceRequest;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.CreatedInvoiceResult;

@Service
public class InvoiceService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final InvoiceBatchRepository invoiceBatchRepository;
    private final InvoiceRecordRepository invoiceRecordRepository;
    private final StarkBankInvoiceClient invoiceClient;
    private final RandomInvoiceFactory randomInvoiceFactory;
    private final InvoiceIssuingProperties properties;
    private final TransactionTemplate transactionTemplate;

    public InvoiceService(
            InvoiceBatchRepository invoiceBatchRepository,
            InvoiceRecordRepository invoiceRecordRepository,
            StarkBankInvoiceClient invoiceClient,
            RandomInvoiceFactory randomInvoiceFactory,
            InvoiceIssuingProperties properties,
            TransactionTemplate transactionTemplate
    ) {
        this.invoiceBatchRepository = invoiceBatchRepository;
        this.invoiceRecordRepository = invoiceRecordRepository;
        this.invoiceClient = invoiceClient;
        this.randomInvoiceFactory = randomInvoiceFactory;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    public InvoiceBatchResult issueManualBatch() {
        return issueBatch(BatchTriggerSource.MANUAL, null);
    }

    public InvoiceBatchResult issueScheduledBatchIfAllowed() {
        int nextSequenceNumber = nextScheduledSequenceNumber();
        if (nextSequenceNumber > properties.getMaxBatches()) {
            return InvoiceBatchResult.skipped(
                    BatchTriggerSource.SCHEDULED,
                    nextSequenceNumber,
                    "Scheduled invoice batch limit reached."
            );
        }

        if (invoiceBatchRepository.existsByTriggerSourceAndSequenceNumber(
                BatchTriggerSource.SCHEDULED,
                nextSequenceNumber
        )) {
            return InvoiceBatchResult.skipped(
                    BatchTriggerSource.SCHEDULED,
                    nextSequenceNumber,
                    "Scheduled invoice batch sequence already exists."
            );
        }

        return issueBatch(BatchTriggerSource.SCHEDULED, nextSequenceNumber);
    }

    public List<InvoiceResponse> listLatestInvoices() {
        return invoiceRecordRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(InvoiceResponse::from)
                .toList();
    }

    public List<InvoiceBatchResponse> listLatestBatches() {
        return invoiceBatchRepository.findTop50ByOrderByStartedAtDesc().stream()
                .map(InvoiceBatchResponse::from)
                .toList();
    }

    static InvoiceStatus mapInvoiceStatus(String status) {
        if (status == null) {
            return InvoiceStatus.UNKNOWN;
        }

        return switch (status.toLowerCase(Locale.ROOT)) {
            case "created" -> InvoiceStatus.CREATED;
            case "paid" -> InvoiceStatus.PAID;
            case "failed" -> InvoiceStatus.FAILED;
            default -> InvoiceStatus.UNKNOWN;
        };
    }

    private int nextScheduledSequenceNumber() {
        return invoiceBatchRepository.findTopByTriggerSourceOrderBySequenceNumberDesc(BatchTriggerSource.SCHEDULED)
                .map(InvoiceBatchEntity::getSequenceNumber)
                .map(sequenceNumber -> sequenceNumber + 1)
                .orElse(1);
    }

    private InvoiceBatchResult issueBatch(BatchTriggerSource triggerSource, Integer sequenceNumber) {
        String batchId = generateBatchId(triggerSource);
        try {
            createStartedBatch(batchId, triggerSource, sequenceNumber);
        } catch (DataIntegrityViolationException exception) {
            if (triggerSource == BatchTriggerSource.SCHEDULED) {
                return InvoiceBatchResult.skipped(
                        triggerSource,
                        sequenceNumber,
                        "Scheduled invoice batch sequence already exists."
                );
            }
            throw exception;
        }

        List<CreateInvoiceRequest> requests = randomInvoiceFactory.generate(batchId, triggerSource);
        try {
            List<CreatedInvoiceResult> createdInvoices = invoiceClient.createInvoices(requests);
            validateCreatedInvoiceCount(requests, createdInvoices);
            return markBatchSucceeded(batchId, triggerSource, sequenceNumber, requests, createdInvoices);
        } catch (RuntimeException exception) {
            return markBatchFailed(batchId, triggerSource, sequenceNumber, exception);
        }
    }

    private void createStartedBatch(String batchId, BatchTriggerSource triggerSource, Integer sequenceNumber) {
        transactionTemplate.executeWithoutResult(status -> invoiceBatchRepository.saveAndFlush(
                new InvoiceBatchEntity(batchId, triggerSource, sequenceNumber, Instant.now())
        ));
    }

    private InvoiceBatchResult markBatchSucceeded(
            String batchId,
            BatchTriggerSource triggerSource,
            Integer sequenceNumber,
            List<CreateInvoiceRequest> requests,
            List<CreatedInvoiceResult> createdInvoices
    ) {
        return transactionTemplate.execute(status -> {
            InvoiceBatchEntity batch = findBatch(batchId);
            List<InvoiceRecordEntity> invoices = IntStream.range(0, createdInvoices.size())
                    .mapToObj(index -> toInvoiceRecord(batchId, requests.get(index), createdInvoices.get(index)))
                    .toList();

            List<InvoiceRecordEntity> savedInvoices = invoiceRecordRepository.saveAll(invoices);
            batch.markSucceeded(savedInvoices.size());
            invoiceBatchRepository.saveAndFlush(batch);

            return new InvoiceBatchResult(
                    batch.getBatchId(),
                    batch.getInvoiceCount(),
                    batch.getStatus().name(),
                    triggerSource,
                    sequenceNumber,
                    savedInvoices.stream().map(InvoiceResult::from).toList(),
                    null
            );
        });
    }

    private InvoiceBatchResult markBatchFailed(
            String batchId,
            BatchTriggerSource triggerSource,
            Integer sequenceNumber,
            RuntimeException exception
    ) {
        return transactionTemplate.execute(status -> {
            InvoiceBatchEntity batch = findBatch(batchId);
            String errorMessage = summarizeError(exception);
            batch.markFailed(errorMessage);
            invoiceBatchRepository.saveAndFlush(batch);

            return new InvoiceBatchResult(
                    batch.getBatchId(),
                    batch.getInvoiceCount(),
                    batch.getStatus().name(),
                    triggerSource,
                    sequenceNumber,
                    List.of(),
                    errorMessage
            );
        });
    }

    private InvoiceBatchEntity findBatch(String batchId) {
        return invoiceBatchRepository.findByBatchId(batchId)
                .orElseThrow(() -> new IllegalStateException("Invoice batch not found: " + batchId));
    }

    private static InvoiceRecordEntity toInvoiceRecord(
            String batchId,
            CreateInvoiceRequest request,
            CreatedInvoiceResult createdInvoice
    ) {
        return new InvoiceRecordEntity(
                createdInvoice.id(),
                batchId,
                createdInvoice.amount() == null ? request.amount() : createdInvoice.amount(),
                request.name(),
                request.taxId(),
                mapInvoiceStatus(createdInvoice.status()),
                createdInvoice.fee(),
                Instant.now()
        );
    }

    private static void validateCreatedInvoiceCount(
            List<CreateInvoiceRequest> requests,
            List<CreatedInvoiceResult> createdInvoices
    ) {
        if (createdInvoices.size() != requests.size()) {
            throw new IllegalStateException(
                    "Stark Bank returned %d invoices for %d requests."
                            .formatted(createdInvoices.size(), requests.size())
            );
        }
    }

    private static String generateBatchId(BatchTriggerSource triggerSource) {
        String source = triggerSource.name().toLowerCase(Locale.ROOT);
        return "invoice-batch-%s-%s".formatted(source, UUID.randomUUID());
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
