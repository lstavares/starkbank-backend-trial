package br.com.leandrotavares.starkbanktrial.application.webhook;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.leandrotavares.starkbanktrial.application.webhook.dto.TransferCreationResult;
import br.com.leandrotavares.starkbanktrial.application.webhook.dto.TransferCreationStatus;
import br.com.leandrotavares.starkbanktrial.application.webhook.dto.TransferRecordResponse;
import br.com.leandrotavares.starkbanktrial.application.webhook.dto.WebhookEventResponse;
import br.com.leandrotavares.starkbanktrial.application.webhook.dto.WebhookResponse;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.TransferRecordEntity;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.WebhookEventRecordEntity;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.WebhookProcessingStatus;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.repository.TransferRecordRepository;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.repository.WebhookEventRecordRepository;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.StarkBankInvoiceClient;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.StarkBankWebhookParser;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.InvoicePaymentDetails;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.ParsedInvoiceEvent;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception.InvalidWebhookSignatureException;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception.StarkBankIntegrationException;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception.UnsupportedWebhookEventException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class WebhookService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final WebhookEventRecordRepository webhookEventRecordRepository;
    private final TransferRecordRepository transferRecordRepository;
    private final StarkBankWebhookParser webhookParser;
    private final StarkBankInvoiceClient invoiceClient;
    private final TransferService transferService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public WebhookService(
            WebhookEventRecordRepository webhookEventRecordRepository,
            TransferRecordRepository transferRecordRepository,
            StarkBankWebhookParser webhookParser,
            StarkBankInvoiceClient invoiceClient,
            TransferService transferService,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate
    ) {
        this.webhookEventRecordRepository = webhookEventRecordRepository;
        this.transferRecordRepository = transferRecordRepository;
        this.webhookParser = webhookParser;
        this.invoiceClient = invoiceClient;
        this.transferService = transferService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public WebhookResponse process(String rawBody, String digitalSignature) {
        if (isBlank(digitalSignature)) {
            throw new InvalidWebhookRequestException("Digital-Signature header is required.");
        }
        if (isBlank(rawBody)) {
            throw new InvalidWebhookRequestException("Webhook payload is required.");
        }

        ParsedInvoiceEvent parsedEvent = parseEvent(rawBody, digitalSignature);
        validateTrustedEvent(parsedEvent);
        JsonNode rawPayload = readRawPayload(rawBody);
        WebhookEventRecordEntity event = getOrCreateReceivedEvent(parsedEvent, rawPayload);

        return switch (event.getStatus()) {
            case PROCESSED -> duplicateResponse(parsedEvent, "Event already processed.");
            case SKIPPED -> duplicateResponse(parsedEvent, "Event already skipped.");
            case FAILED -> new WebhookResponse(
                    "FAILED",
                    parsedEvent.eventId(),
                    parsedEvent.invoiceId(),
                    null,
                    "Event already failed and will not be reprocessed in this phase."
            );
            case RECEIVED -> processReceivedEvent(parsedEvent);
        };
    }

    public List<WebhookEventResponse> listLatestWebhookEvents() {
        return webhookEventRecordRepository.findTop50ByOrderByReceivedAtDesc().stream()
                .map(WebhookEventResponse::from)
                .toList();
    }

    public List<TransferRecordResponse> listLatestTransfers() {
        return transferRecordRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(TransferRecordResponse::from)
                .toList();
    }

    private WebhookResponse processReceivedEvent(ParsedInvoiceEvent parsedEvent) {
        if (!isPaidInvoiceEvent(parsedEvent)) {
            String reason = "Invoice event is not paid and was skipped.";
            markEventSkipped(parsedEvent.eventId(), reason);
            return new WebhookResponse("SKIPPED", parsedEvent.eventId(), parsedEvent.invoiceId(), null, reason);
        }

        if (isBlank(parsedEvent.invoiceId())) {
            return failEvent(parsedEvent, "Invoice id is required to create a transfer.");
        }

        Optional<TransferRecordEntity> existingTransfer = findExistingTransfer(
                parsedEvent.invoiceId(),
                externalId(parsedEvent.eventId())
        );
        if (existingTransfer.isPresent()) {
            String message = "Transfer already exists for this invoice or event.";
            markEventSkipped(parsedEvent.eventId(), message);
            return new WebhookResponse(
                    "SKIPPED",
                    parsedEvent.eventId(),
                    parsedEvent.invoiceId(),
                    existingTransfer.get().getStarkTransferId(),
                    message
            );
        }

        AmountResolution amountResolution = resolveAmounts(parsedEvent);
        if (amountResolution.failed()) {
            return failEvent(parsedEvent, amountResolution.errorMessage());
        }

        TransferCreationResult transferResult = transferService.createTransfer(
                parsedEvent.eventId(),
                parsedEvent.invoiceId(),
                amountResolution.receivedAmount(),
                amountResolution.fee(),
                amountResolution.transferAmount()
        );

        if (transferResult.status() == TransferCreationStatus.SUCCEEDED) {
            markEventProcessed(parsedEvent.eventId());
            return new WebhookResponse(
                    "PROCESSED",
                    parsedEvent.eventId(),
                    parsedEvent.invoiceId(),
                    transferResult.transferId(),
                    transferResult.message()
            );
        }

        if (transferResult.status() == TransferCreationStatus.SKIPPED) {
            markEventSkipped(parsedEvent.eventId(), transferResult.message());
            return new WebhookResponse(
                    "SKIPPED",
                    parsedEvent.eventId(),
                    parsedEvent.invoiceId(),
                    transferResult.transferId(),
                    transferResult.message()
            );
        }

        markEventFailed(parsedEvent.eventId(), transferResult.message());
        return new WebhookResponse(
                "FAILED",
                parsedEvent.eventId(),
                parsedEvent.invoiceId(),
                null,
                transferResult.message()
        );
    }

    private ParsedInvoiceEvent parseEvent(String rawBody, String digitalSignature) {
        try {
            return webhookParser.parseInvoiceEvent(rawBody, digitalSignature);
        } catch (InvalidWebhookSignatureException | UnsupportedWebhookEventException exception) {
            throw exception;
        } catch (StarkBankIntegrationException exception) {
            throw new InvalidWebhookRequestException("Invalid Stark Bank webhook payload.", exception);
        }
    }

    private void validateTrustedEvent(ParsedInvoiceEvent parsedEvent) {
        if (parsedEvent == null || isBlank(parsedEvent.eventId())) {
            throw new InvalidWebhookRequestException("Webhook event id is required.");
        }
        if (isBlank(parsedEvent.subscription())) {
            throw new InvalidWebhookRequestException("Webhook event subscription is required.");
        }
    }

    private JsonNode readRawPayload(String rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (JsonProcessingException exception) {
            throw new InvalidWebhookRequestException("Webhook payload must be valid JSON.", exception);
        }
    }

    private WebhookEventRecordEntity getOrCreateReceivedEvent(ParsedInvoiceEvent parsedEvent, JsonNode rawPayload) {
        return webhookEventRecordRepository.findByStarkEventId(parsedEvent.eventId())
                .orElseGet(() -> createReceivedEvent(parsedEvent, rawPayload));
    }

    private WebhookEventRecordEntity createReceivedEvent(ParsedInvoiceEvent parsedEvent, JsonNode rawPayload) {
        try {
            return transactionTemplate.execute(status -> webhookEventRecordRepository.saveAndFlush(
                    new WebhookEventRecordEntity(
                            parsedEvent.eventId(),
                            parsedEvent.subscription(),
                            parsedEvent.invoiceId(),
                            parsedEvent.invoiceLogId(),
                            parsedEvent.logType(),
                            rawPayload,
                            Instant.now()
                    )
            ));
        } catch (DataIntegrityViolationException exception) {
            return webhookEventRecordRepository.findByStarkEventId(parsedEvent.eventId())
                    .orElseThrow(() -> exception);
        }
    }

    private Optional<TransferRecordEntity> findExistingTransfer(String invoiceId, String externalId) {
        Optional<TransferRecordEntity> transferByInvoice = transferRecordRepository.findByInvoiceId(invoiceId);
        if (transferByInvoice.isPresent()) {
            return transferByInvoice;
        }
        return transferRecordRepository.findByExternalId(externalId);
    }

    private AmountResolution resolveAmounts(ParsedInvoiceEvent parsedEvent) {
        InvoicePaymentDetails paymentDetails = null;
        if (parsedEvent.amount() == null || !parsedEvent.feeKnown()) {
            PaymentDetailsResolution detailsResolution = fetchPaymentDetails(parsedEvent.invoiceId());
            if (detailsResolution.failed()) {
                return AmountResolution.failed(detailsResolution.errorMessage());
            }
            paymentDetails = detailsResolution.paymentDetails();
        }

        Long receivedAmount = parsedEvent.amount();
        if (receivedAmount == null && paymentDetails != null) {
            receivedAmount = paymentDetails.paymentAmount() == null
                    ? paymentDetails.invoiceAmount()
                    : paymentDetails.paymentAmount();
        }

        Long fee = parsedEvent.feeKnown() ? parsedEvent.fee() : null;
        if (fee == null && paymentDetails != null && paymentDetails.invoiceFeeKnown()) {
            fee = paymentDetails.invoiceFee();
        }

        if (receivedAmount == null) {
            return AmountResolution.failed("Unable to determine invoice received amount.");
        }
        if (fee == null) {
            return AmountResolution.failed("Unable to determine invoice fee.");
        }
        if (receivedAmount <= 0) {
            return AmountResolution.failed("Invoice received amount must be greater than zero.");
        }
        if (fee < 0) {
            return AmountResolution.failed("Invoice fee must not be negative.");
        }

        long transferAmount = receivedAmount - fee;
        if (transferAmount <= 0) {
            return AmountResolution.failed("Transfer amount must be greater than zero.");
        }

        return AmountResolution.succeeded(receivedAmount, fee, transferAmount);
    }

    private PaymentDetailsResolution fetchPaymentDetails(String invoiceId) {
        try {
            return PaymentDetailsResolution.succeeded(invoiceClient.getPaymentDetails(invoiceId));
        } catch (RuntimeException exception) {
            return PaymentDetailsResolution.failed(
                    "Unable to fetch invoice payment details: " + summarizeError(exception)
            );
        }
    }

    private WebhookResponse failEvent(ParsedInvoiceEvent parsedEvent, String message) {
        markEventFailed(parsedEvent.eventId(), message);
        return new WebhookResponse("FAILED", parsedEvent.eventId(), parsedEvent.invoiceId(), null, message);
    }

    private void markEventProcessed(String eventId) {
        updateEvent(eventId, WebhookEventRecordEntity::markProcessed);
    }

    private void markEventSkipped(String eventId, String reason) {
        updateEvent(eventId, event -> event.markSkipped(reason));
    }

    private void markEventFailed(String eventId, String errorMessage) {
        updateEvent(eventId, event -> event.markFailed(errorMessage));
    }

    private WebhookEventRecordEntity updateEvent(String eventId, Consumer<WebhookEventRecordEntity> update) {
        return transactionTemplate.execute(status -> {
            WebhookEventRecordEntity event = webhookEventRecordRepository.findByStarkEventId(eventId)
                    .orElseThrow(() -> new IllegalStateException("Webhook event record not found: " + eventId));
            update.accept(event);
            return webhookEventRecordRepository.saveAndFlush(event);
        });
    }

    private WebhookResponse duplicateResponse(ParsedInvoiceEvent parsedEvent, String message) {
        return new WebhookResponse("DUPLICATE", parsedEvent.eventId(), parsedEvent.invoiceId(), null, message);
    }

    private static boolean isPaidInvoiceEvent(ParsedInvoiceEvent parsedEvent) {
        return equalsIgnoreCase(parsedEvent.subscription(), "invoice")
                && equalsIgnoreCase(parsedEvent.logType(), "paid")
                && equalsIgnoreCase(parsedEvent.status(), "paid");
    }

    private static boolean equalsIgnoreCase(String actual, String expected) {
        return actual != null && actual.toLowerCase(Locale.ROOT).equals(expected);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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

    private record AmountResolution(
            Long receivedAmount,
            Long fee,
            Long transferAmount,
            String errorMessage
    ) {

        private static AmountResolution succeeded(Long receivedAmount, Long fee, Long transferAmount) {
            return new AmountResolution(receivedAmount, fee, transferAmount, null);
        }

        private static AmountResolution failed(String errorMessage) {
            return new AmountResolution(null, null, null, errorMessage);
        }

        private boolean failed() {
            return errorMessage != null;
        }
    }

    private record PaymentDetailsResolution(
            InvoicePaymentDetails paymentDetails,
            String errorMessage
    ) {

        private static PaymentDetailsResolution succeeded(InvoicePaymentDetails paymentDetails) {
            return new PaymentDetailsResolution(paymentDetails, null);
        }

        private static PaymentDetailsResolution failed(String errorMessage) {
            return new PaymentDetailsResolution(null, errorMessage);
        }

        private boolean failed() {
            return errorMessage != null;
        }
    }
}
