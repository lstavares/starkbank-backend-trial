package br.com.leandrotavares.starkbanktrial.application.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.leandrotavares.starkbanktrial.application.webhook.dto.WebhookResponse;
import br.com.leandrotavares.starkbanktrial.config.TransferDestinationProperties;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.TransferRecordEntity;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.WebhookEventRecordEntity;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.TransferStatus;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.WebhookProcessingStatus;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.repository.TransferRecordRepository;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.repository.WebhookEventRecordRepository;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.StarkBankInvoiceClient;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.StarkBankTransferClient;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.StarkBankWebhookParser;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.CreateTransferRequest;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.CreatedTransferResult;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.InvoicePaymentDetails;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.ParsedInvoiceEvent;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception.StarkBankIntegrationException;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    private static final String RAW_BODY = "{\"event\":{\"id\":\"evt_001\"}}";
    private static final String DIGITAL_SIGNATURE = "valid-signature";
    private static final Instant BASE_TIME = Instant.parse("2026-06-19T12:00:00Z");

    @Mock
    private WebhookEventRecordRepository webhookEventRecordRepository;

    @Mock
    private TransferRecordRepository transferRecordRepository;

    @Mock
    private StarkBankWebhookParser webhookParser;

    @Mock
    private StarkBankInvoiceClient invoiceClient;

    @Mock
    private StarkBankTransferClient transferClient;

    private RepositoryState state;
    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        state = new RepositoryState();
        configureRepositories();

        TransactionTemplate transactionTemplate = transactionTemplate();
        TransferService transferService = new TransferService(
                transferRecordRepository,
                transferClient,
                new TransferDestinationProperties(),
                transactionTemplate
        );
        webhookService = new WebhookService(
                webhookEventRecordRepository,
                transferRecordRepository,
                webhookParser,
                invoiceClient,
                transferService,
                new ObjectMapper(),
                transactionTemplate
        );
    }

    @Test
    void shouldCreateTransferForPaidEventWithKnownFee() {
        ParsedInvoiceEvent parsedEvent = paidEvent("evt_001", "inv_001", 1_000L, 10L, true);
        whenParsed(parsedEvent);
        when(transferClient.createTransfer(any(CreateTransferRequest.class))).thenReturn(new CreatedTransferResult(
                "trf_001",
                "transfer-evt_001",
                990L,
                null,
                "created"
        ));

        WebhookResponse response = webhookService.process(RAW_BODY, DIGITAL_SIGNATURE);

        assertThat(response.status()).isEqualTo("PROCESSED");
        assertThat(response.eventId()).isEqualTo("evt_001");
        assertThat(response.invoiceId()).isEqualTo("inv_001");
        assertThat(response.transferId()).isEqualTo("trf_001");
        assertThat(state.events.get("evt_001").getStatus()).isEqualTo(WebhookProcessingStatus.PROCESSED);

        TransferRecordEntity transfer = state.transfersByExternalId.get("transfer-evt_001");
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.SUCCEEDED);
        assertThat(transfer.getGrossAmount()).isEqualTo(1_000L);
        assertThat(transfer.getFeeAmount()).isEqualTo(10L);
        assertThat(transfer.getNetAmount()).isEqualTo(990L);
        assertThat(transfer.getStarkTransferId()).isEqualTo("trf_001");

        ArgumentCaptor<CreateTransferRequest> requestCaptor = ArgumentCaptor.forClass(CreateTransferRequest.class);
        verify(transferClient).createTransfer(requestCaptor.capture());
        CreateTransferRequest request = requestCaptor.getValue();
        assertThat(request.amount()).isEqualTo(990L);
        assertThat(request.bankCode()).isEqualTo("20018183");
        assertThat(request.branchCode()).isEqualTo("0001");
        assertThat(request.accountNumber()).isEqualTo("6341320293482496");
        assertThat(request.accountType()).isEqualTo("payment");
        assertThat(request.taxId()).isEqualTo("20.018.183/0001-80");
        assertThat(request.name()).isEqualTo("Stark Bank S.A.");
        assertThat(request.externalId()).isEqualTo("transfer-evt_001");
        assertThat(request.tags()).containsExactly("trial", "event:evt_001", "invoice:inv_001");
        verifyNoInteractions(invoiceClient);
    }

    @Test
    void shouldFetchPaymentDetailsWhenFeeIsUnknown() {
        ParsedInvoiceEvent parsedEvent = paidEvent("evt_001", "inv_001", 1_000L, null, false);
        whenParsed(parsedEvent);
        when(invoiceClient.getPaymentDetails("inv_001")).thenReturn(new InvoicePaymentDetails(
                "inv_001",
                "paid",
                1_000L,
                25L,
                true,
                1_000L,
                "pix",
                "E123"
        ));
        when(transferClient.createTransfer(any(CreateTransferRequest.class))).thenReturn(new CreatedTransferResult(
                "trf_001",
                "transfer-evt_001",
                975L,
                null,
                "created"
        ));

        WebhookResponse response = webhookService.process(RAW_BODY, DIGITAL_SIGNATURE);

        assertThat(response.status()).isEqualTo("PROCESSED");
        assertThat(state.transfersByExternalId.get("transfer-evt_001").getNetAmount()).isEqualTo(975L);
        verify(invoiceClient).getPaymentDetails("inv_001");
    }

    @Test
    void shouldFailWithoutTransferWhenFeeCannotBeDetermined() {
        ParsedInvoiceEvent parsedEvent = paidEvent("evt_001", "inv_001", 1_000L, null, false);
        whenParsed(parsedEvent);
        when(invoiceClient.getPaymentDetails("inv_001")).thenReturn(new InvoicePaymentDetails(
                "inv_001",
                "paid",
                1_000L,
                null,
                false,
                1_000L,
                "pix",
                "E123"
        ));

        WebhookResponse response = webhookService.process(RAW_BODY, DIGITAL_SIGNATURE);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.message()).isEqualTo("Unable to determine invoice fee.");
        assertThat(state.events.get("evt_001").getStatus()).isEqualTo(WebhookProcessingStatus.FAILED);
        assertThat(state.transfersByExternalId).isEmpty();
        verifyNoInteractions(transferClient);
    }

    @Test
    void shouldFailWithoutTransferWhenAmountCannotBeDetermined() {
        ParsedInvoiceEvent parsedEvent = paidEvent("evt_001", "inv_001", null, 10L, true);
        whenParsed(parsedEvent);
        when(invoiceClient.getPaymentDetails("inv_001")).thenReturn(new InvoicePaymentDetails(
                "inv_001",
                "paid",
                null,
                10L,
                true,
                null,
                "pix",
                "E123"
        ));

        WebhookResponse response = webhookService.process(RAW_BODY, DIGITAL_SIGNATURE);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.message()).isEqualTo("Unable to determine invoice received amount.");
        assertThat(state.events.get("evt_001").getStatus()).isEqualTo(WebhookProcessingStatus.FAILED);
        assertThat(state.transfersByExternalId).isEmpty();
        verifyNoInteractions(transferClient);
    }

    @Test
    void shouldFailWithoutTransferWhenTransferAmountIsNotPositive() {
        ParsedInvoiceEvent parsedEvent = paidEvent("evt_001", "inv_001", 10L, 10L, true);
        whenParsed(parsedEvent);

        WebhookResponse response = webhookService.process(RAW_BODY, DIGITAL_SIGNATURE);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.message()).isEqualTo("Transfer amount must be greater than zero.");
        assertThat(state.events.get("evt_001").getStatus()).isEqualTo(WebhookProcessingStatus.FAILED);
        assertThat(state.transfersByExternalId).isEmpty();
        verifyNoInteractions(invoiceClient, transferClient);
    }

    @Test
    void shouldSkipInvoiceEventThatIsNotPaid() {
        ParsedInvoiceEvent parsedEvent = new ParsedInvoiceEvent(
                "evt_001",
                "invoice",
                "inv_001",
                "log_001",
                "created",
                "created",
                1_000L,
                10L,
                true
        );
        whenParsed(parsedEvent);

        WebhookResponse response = webhookService.process(RAW_BODY, DIGITAL_SIGNATURE);

        assertThat(response.status()).isEqualTo("SKIPPED");
        assertThat(state.events.get("evt_001").getStatus()).isEqualTo(WebhookProcessingStatus.SKIPPED);
        verifyNoInteractions(invoiceClient, transferClient);
    }

    @Test
    void shouldNotCallClientsForDuplicatedProcessedEvent() throws Exception {
        ParsedInvoiceEvent parsedEvent = paidEvent("evt_001", "inv_001", 1_000L, 10L, true);
        WebhookEventRecordEntity existingEvent = eventRecord(parsedEvent);
        existingEvent.markProcessed();
        state.putEvent(existingEvent);
        whenParsed(parsedEvent);

        WebhookResponse response = webhookService.process(RAW_BODY, DIGITAL_SIGNATURE);

        assertThat(response.status()).isEqualTo("DUPLICATE");
        verifyNoInteractions(invoiceClient, transferClient);
    }

    @Test
    void shouldNotCallClientsForDuplicatedSkippedEvent() throws Exception {
        ParsedInvoiceEvent parsedEvent = paidEvent("evt_001", "inv_001", 1_000L, 10L, true);
        WebhookEventRecordEntity existingEvent = eventRecord(parsedEvent);
        existingEvent.markSkipped("Already skipped.");
        state.putEvent(existingEvent);
        whenParsed(parsedEvent);

        WebhookResponse response = webhookService.process(RAW_BODY, DIGITAL_SIGNATURE);

        assertThat(response.status()).isEqualTo("DUPLICATE");
        verifyNoInteractions(invoiceClient, transferClient);
    }

    @Test
    void shouldNotReprocessDuplicatedFailedEvent() throws Exception {
        ParsedInvoiceEvent parsedEvent = paidEvent("evt_001", "inv_001", 1_000L, 10L, true);
        WebhookEventRecordEntity existingEvent = eventRecord(parsedEvent);
        existingEvent.markFailed("Previous failure.");
        state.putEvent(existingEvent);
        whenParsed(parsedEvent);

        WebhookResponse response = webhookService.process(RAW_BODY, DIGITAL_SIGNATURE);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.message()).isEqualTo("Event already failed and will not be reprocessed in this phase.");
        verifyNoInteractions(invoiceClient, transferClient);
    }

    @Test
    void shouldContinueProcessingDuplicatedReceivedEventWhenTransferDoesNotExist() throws Exception {
        ParsedInvoiceEvent parsedEvent = paidEvent("evt_001", "inv_001", 1_000L, 10L, true);
        state.putEvent(eventRecord(parsedEvent));
        whenParsed(parsedEvent);
        when(transferClient.createTransfer(any(CreateTransferRequest.class))).thenReturn(new CreatedTransferResult(
                "trf_001",
                "transfer-evt_001",
                990L,
                null,
                "created"
        ));

        WebhookResponse response = webhookService.process(RAW_BODY, DIGITAL_SIGNATURE);

        assertThat(response.status()).isEqualTo("PROCESSED");
        assertThat(state.events.get("evt_001").getStatus()).isEqualTo(WebhookProcessingStatus.PROCESSED);
        verify(transferClient).createTransfer(any(CreateTransferRequest.class));
    }

    @Test
    void shouldSkipWhenTransferAlreadyExistsForInvoice() {
        ParsedInvoiceEvent parsedEvent = paidEvent("evt_001", "inv_001", null, null, false);
        state.putTransfer(existingTransfer("inv_001", "evt_other", "transfer-other"));
        whenParsed(parsedEvent);

        WebhookResponse response = webhookService.process(RAW_BODY, DIGITAL_SIGNATURE);

        assertThat(response.status()).isEqualTo("SKIPPED");
        assertThat(response.message()).isEqualTo("Transfer already exists for this invoice or event.");
        assertThat(state.events.get("evt_001").getStatus()).isEqualTo(WebhookProcessingStatus.SKIPPED);
        verifyNoInteractions(invoiceClient, transferClient);
    }

    @Test
    void shouldSkipWhenTransferAlreadyExistsForExternalId() {
        ParsedInvoiceEvent parsedEvent = paidEvent("evt_001", "inv_001", 1_000L, 10L, true);
        state.putTransfer(existingTransfer("inv_other", "evt_other", "transfer-evt_001"));
        whenParsed(parsedEvent);

        WebhookResponse response = webhookService.process(RAW_BODY, DIGITAL_SIGNATURE);

        assertThat(response.status()).isEqualTo("SKIPPED");
        assertThat(response.message()).isEqualTo("Transfer already exists for this invoice or event.");
        assertThat(state.events.get("evt_001").getStatus()).isEqualTo(WebhookProcessingStatus.SKIPPED);
        verifyNoInteractions(invoiceClient, transferClient);
    }

    @Test
    void shouldMarkTransferAndEventAsFailedWhenTransferClientFails() {
        ParsedInvoiceEvent parsedEvent = paidEvent("evt_001", "inv_001", 1_000L, 10L, true);
        whenParsed(parsedEvent);
        when(transferClient.createTransfer(any(CreateTransferRequest.class))).thenThrow(
                new StarkBankIntegrationException("Unable to create Stark Bank transfer.", null)
        );

        WebhookResponse response = webhookService.process(RAW_BODY, DIGITAL_SIGNATURE);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.message()).isEqualTo("Unable to create Stark Bank transfer.");
        assertThat(state.events.get("evt_001").getStatus()).isEqualTo(WebhookProcessingStatus.FAILED);
        assertThat(state.transfersByExternalId.get("transfer-evt_001").getStatus()).isEqualTo(TransferStatus.FAILED);
    }

    @Test
    void shouldReloadEventAfterUniqueConstraintViolationAndContinueProcessing() throws Exception {
        ParsedInvoiceEvent parsedEvent = paidEvent("evt_001", "inv_001", 1_000L, 10L, true);
        state.throwDuplicateOnEventSave = true;
        state.eventAfterDuplicate = eventRecord(parsedEvent);
        whenParsed(parsedEvent);
        when(transferClient.createTransfer(any(CreateTransferRequest.class))).thenReturn(new CreatedTransferResult(
                "trf_001",
                "transfer-evt_001",
                990L,
                null,
                "created"
        ));

        WebhookResponse response = webhookService.process(RAW_BODY, DIGITAL_SIGNATURE);

        assertThat(response.status()).isEqualTo("PROCESSED");
        assertThat(state.events.get("evt_001").getStatus()).isEqualTo(WebhookProcessingStatus.PROCESSED);
        verify(transferClient).createTransfer(any(CreateTransferRequest.class));
    }

    private void configureRepositories() {
        lenient().when(webhookEventRecordRepository.findByStarkEventId(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(
                        state.events.get(invocation.getArgument(0, String.class))
                ));
        lenient().when(webhookEventRecordRepository.saveAndFlush(any(WebhookEventRecordEntity.class)))
                .thenAnswer(invocation -> state.saveEvent(invocation.getArgument(0)));
        lenient().when(webhookEventRecordRepository.findTop50ByOrderByReceivedAtDesc())
                .thenAnswer(invocation -> List.copyOf(state.events.values()));

        lenient().when(transferRecordRepository.findByInvoiceId(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(
                        state.transfersByInvoiceId.get(invocation.getArgument(0, String.class))
                ));
        lenient().when(transferRecordRepository.findByExternalId(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(
                        state.transfersByExternalId.get(invocation.getArgument(0, String.class))
                ));
        lenient().when(transferRecordRepository.saveAndFlush(any(TransferRecordEntity.class)))
                .thenAnswer(invocation -> state.saveTransfer(invocation.getArgument(0)));
        lenient().when(transferRecordRepository.findTop50ByOrderByCreatedAtDesc())
                .thenAnswer(invocation -> List.copyOf(state.transfersByExternalId.values()));
    }

    private void whenParsed(ParsedInvoiceEvent parsedEvent) {
        when(webhookParser.parseInvoiceEvent(RAW_BODY, DIGITAL_SIGNATURE)).thenReturn(parsedEvent);
    }

    private static ParsedInvoiceEvent paidEvent(
            String eventId,
            String invoiceId,
            Long amount,
            Long fee,
            boolean feeKnown
    ) {
        return new ParsedInvoiceEvent(eventId, "invoice", invoiceId, "log_001", "paid", "paid", amount, fee, feeKnown);
    }

    private static WebhookEventRecordEntity eventRecord(ParsedInvoiceEvent parsedEvent) throws Exception {
        return new WebhookEventRecordEntity(
                parsedEvent.eventId(),
                parsedEvent.subscription(),
                parsedEvent.invoiceId(),
                parsedEvent.invoiceLogId(),
                parsedEvent.logType(),
                new ObjectMapper().readTree(RAW_BODY),
                BASE_TIME
        );
    }

    private static TransferRecordEntity existingTransfer(String invoiceId, String eventId, String externalId) {
        return new TransferRecordEntity(invoiceId, eventId, externalId, 1_000L, 10L, 990L, BASE_TIME);
    }

    private static TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
    }

    private static class RepositoryState {

        private final Map<String, WebhookEventRecordEntity> events = new LinkedHashMap<>();
        private final Map<String, TransferRecordEntity> transfersByExternalId = new LinkedHashMap<>();
        private final Map<String, TransferRecordEntity> transfersByInvoiceId = new LinkedHashMap<>();
        private boolean throwDuplicateOnEventSave;
        private WebhookEventRecordEntity eventAfterDuplicate;

        private WebhookEventRecordEntity saveEvent(WebhookEventRecordEntity event) {
            if (throwDuplicateOnEventSave) {
                throwDuplicateOnEventSave = false;
                if (eventAfterDuplicate != null) {
                    putEvent(eventAfterDuplicate);
                }
                throw new DataIntegrityViolationException("duplicate webhook event");
            }
            putEvent(event);
            return event;
        }

        private TransferRecordEntity saveTransfer(TransferRecordEntity transfer) {
            putTransfer(transfer);
            return transfer;
        }

        private void putEvent(WebhookEventRecordEntity event) {
            events.put(event.getStarkEventId(), event);
        }

        private void putTransfer(TransferRecordEntity transfer) {
            transfersByExternalId.put(transfer.getExternalId(), transfer);
            transfersByInvoiceId.put(transfer.getInvoiceId(), transfer);
        }
    }
}
