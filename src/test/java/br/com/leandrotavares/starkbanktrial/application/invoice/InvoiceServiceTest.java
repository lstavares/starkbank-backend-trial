package br.com.leandrotavares.starkbanktrial.application.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.leandrotavares.starkbanktrial.config.InvoiceIssuingProperties;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.InvoiceBatchEntity;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.InvoiceRecordEntity;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.BatchStatus;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.BatchTriggerSource;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.InvoiceStatus;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.repository.InvoiceBatchRepository;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.repository.InvoiceRecordRepository;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.StarkBankInvoiceClient;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.CreateInvoiceRequest;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.CreatedInvoiceResult;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceServiceTest {

    @Mock
    private InvoiceBatchRepository invoiceBatchRepository;

    @Mock
    private InvoiceRecordRepository invoiceRecordRepository;

    @Mock
    private StarkBankInvoiceClient invoiceClient;

    @Mock
    private RandomInvoiceFactory randomInvoiceFactory;

    private final InvoiceIssuingProperties properties = new InvoiceIssuingProperties();
    private final Map<String, InvoiceBatchEntity> batches = new LinkedHashMap<>();
    private final List<InvoiceRecordEntity> invoices = new ArrayList<>();

    private InvoiceService service;

    @BeforeEach
    void setUp() {
        properties.setMaxBatches(8);
        batches.clear();
        invoices.clear();

        when(invoiceBatchRepository.saveAndFlush(any(InvoiceBatchEntity.class))).thenAnswer(invocation -> {
            InvoiceBatchEntity batch = invocation.getArgument(0);
            batches.put(batch.getBatchId(), batch);
            return batch;
        });
        when(invoiceBatchRepository.findByBatchId(anyString())).thenAnswer(invocation -> {
            String batchId = invocation.getArgument(0);
            return Optional.ofNullable(batches.get(batchId));
        });
        when(invoiceRecordRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<InvoiceRecordEntity> records = invocation.getArgument(0);
            List<InvoiceRecordEntity> savedRecords = StreamSupport.stream(records.spliterator(), false).toList();
            invoices.addAll(savedRecords);
            return savedRecords;
        });

        service = new InvoiceService(
                invoiceBatchRepository,
                invoiceRecordRepository,
                invoiceClient,
                randomInvoiceFactory,
                properties,
                transactionTemplate()
        );
    }

    @Test
    void shouldIssueManualBatchAndPersistInvoices() {
        List<CreateInvoiceRequest> requests = List.of(
                request(1_000L, "Ada Lovelace", "11144477735"),
                request(2_000L, "Grace Hopper", "52998224725")
        );
        when(randomInvoiceFactory.generate(anyString(), eq(BatchTriggerSource.MANUAL))).thenReturn(requests);
        when(invoiceClient.createInvoices(requests)).thenReturn(List.of(
                new CreatedInvoiceResult("inv_001", 1_000L, 10L, "created"),
                new CreatedInvoiceResult("inv_002", 2_000L, 20L, "paid")
        ));

        var result = service.issueManualBatch();

        InvoiceBatchEntity batch = batches.values().iterator().next();
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.triggerSource()).isEqualTo(BatchTriggerSource.MANUAL);
        assertThat(result.sequenceNumber()).isNull();
        assertThat(result.invoiceCount()).isEqualTo(2);
        assertThat(batch.getTriggerSource()).isEqualTo(BatchTriggerSource.MANUAL);
        assertThat(batch.getSequenceNumber()).isNull();
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.SUCCEEDED);
        assertThat(invoices)
                .extracting(InvoiceRecordEntity::getBatchId)
                .containsExactly(batch.getBatchId(), batch.getBatchId());
        assertThat(invoices)
                .extracting(InvoiceRecordEntity::getStatus)
                .containsExactly(InvoiceStatus.CREATED, InvoiceStatus.PAID);
        verify(invoiceBatchRepository, never())
                .findTopByTriggerSourceOrderBySequenceNumberDesc(BatchTriggerSource.SCHEDULED);
    }

    @Test
    void shouldIssueScheduledBatchWithNextSequence() {
        InvoiceBatchEntity previousBatch = new InvoiceBatchEntity(
                "batch-007",
                BatchTriggerSource.SCHEDULED,
                7,
                Instant.parse("2026-06-19T12:00:00Z")
        );
        when(invoiceBatchRepository.findTopByTriggerSourceOrderBySequenceNumberDesc(BatchTriggerSource.SCHEDULED))
                .thenReturn(Optional.of(previousBatch));
        when(invoiceBatchRepository.existsByTriggerSourceAndSequenceNumber(BatchTriggerSource.SCHEDULED, 8))
                .thenReturn(false);

        List<CreateInvoiceRequest> requests = List.of(request(1_000L, "Ada Lovelace", "11144477735"));
        when(randomInvoiceFactory.generate(anyString(), eq(BatchTriggerSource.SCHEDULED))).thenReturn(requests);
        when(invoiceClient.createInvoices(requests)).thenReturn(List.of(
                new CreatedInvoiceResult("inv_001", 1_000L, null, "created")
        ));

        var result = service.issueScheduledBatchIfAllowed();

        InvoiceBatchEntity batch = batches.values().iterator().next();
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.sequenceNumber()).isEqualTo(8);
        assertThat(batch.getTriggerSource()).isEqualTo(BatchTriggerSource.SCHEDULED);
        assertThat(batch.getSequenceNumber()).isEqualTo(8);
        verify(invoiceBatchRepository, never()).countByTriggerSource(BatchTriggerSource.SCHEDULED);
    }

    @Test
    void shouldSkipScheduledBatchWhenLimitIsReached() {
        InvoiceBatchEntity previousBatch = new InvoiceBatchEntity(
                "batch-008",
                BatchTriggerSource.SCHEDULED,
                8,
                Instant.parse("2026-06-19T12:00:00Z")
        );
        when(invoiceBatchRepository.findTopByTriggerSourceOrderBySequenceNumberDesc(BatchTriggerSource.SCHEDULED))
                .thenReturn(Optional.of(previousBatch));

        var result = service.issueScheduledBatchIfAllowed();

        assertThat(result.status()).isEqualTo("SKIPPED");
        assertThat(result.sequenceNumber()).isEqualTo(9);
        assertThat(batches).isEmpty();
        verify(invoiceClient, never()).createInvoices(any());
    }

    @Test
    void shouldSkipScheduledBatchWhenSequenceAlreadyExists() {
        InvoiceBatchEntity previousBatch = new InvoiceBatchEntity(
                "batch-002",
                BatchTriggerSource.SCHEDULED,
                2,
                Instant.parse("2026-06-19T12:00:00Z")
        );
        when(invoiceBatchRepository.findTopByTriggerSourceOrderBySequenceNumberDesc(BatchTriggerSource.SCHEDULED))
                .thenReturn(Optional.of(previousBatch));
        when(invoiceBatchRepository.existsByTriggerSourceAndSequenceNumber(BatchTriggerSource.SCHEDULED, 3))
                .thenReturn(true);

        var result = service.issueScheduledBatchIfAllowed();

        assertThat(result.status()).isEqualTo("SKIPPED");
        assertThat(result.sequenceNumber()).isEqualTo(3);
        assertThat(batches).isEmpty();
        verify(invoiceClient, never()).createInvoices(any());
    }

    @Test
    void shouldMarkBatchFailedWhenClientFails() {
        List<CreateInvoiceRequest> requests = List.of(request(1_000L, "Ada Lovelace", "11144477735"));
        when(randomInvoiceFactory.generate(anyString(), eq(BatchTriggerSource.MANUAL))).thenReturn(requests);
        when(invoiceClient.createInvoices(requests)).thenThrow(new RuntimeException("Stark unavailable"));

        var result = service.issueManualBatch();

        InvoiceBatchEntity batch = batches.values().iterator().next();
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.message()).isEqualTo("Stark unavailable");
        assertThat(result.invoices()).isEmpty();
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(batch.getErrorMessage()).isEqualTo("Stark unavailable");
        assertThat(invoices).isEmpty();
    }

    @Test
    void shouldMapStarkInvoiceStatuses() {
        assertThat(InvoiceService.mapInvoiceStatus("created")).isEqualTo(InvoiceStatus.CREATED);
        assertThat(InvoiceService.mapInvoiceStatus("paid")).isEqualTo(InvoiceStatus.PAID);
        assertThat(InvoiceService.mapInvoiceStatus("failed")).isEqualTo(InvoiceStatus.FAILED);
        assertThat(InvoiceService.mapInvoiceStatus("expired")).isEqualTo(InvoiceStatus.UNKNOWN);
        assertThat(InvoiceService.mapInvoiceStatus(null)).isEqualTo(InvoiceStatus.UNKNOWN);
    }

    private static CreateInvoiceRequest request(Long amount, String name, String taxId) {
        return new CreateInvoiceRequest(amount, name, taxId, List.of("trial"), List.of());
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
}
