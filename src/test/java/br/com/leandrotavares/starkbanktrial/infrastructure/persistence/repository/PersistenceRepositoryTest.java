package br.com.leandrotavares.starkbanktrial.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.InvoiceBatchEntity;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.InvoiceRecordEntity;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.TransferRecordEntity;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.WebhookEventRecordEntity;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.BatchStatus;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.BatchTriggerSource;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.InvoiceStatus;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.TransferStatus;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.WebhookProcessingStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersistenceRepositoryTest {

    private static final Instant BASE_TIME = Instant.parse("2026-06-19T12:00:00Z");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("starkbank_trial_test")
            .withUsername("starkbank")
            .withPassword("starkbank");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private InvoiceBatchRepository invoiceBatchRepository;

    @Autowired
    private InvoiceRecordRepository invoiceRecordRepository;

    @Autowired
    private WebhookEventRecordRepository webhookEventRecordRepository;

    @Autowired
    private TransferRecordRepository transferRecordRepository;

    @Test
    void shouldPersistEntitiesAndResolveRepositoryQueries() {
        InvoiceBatchEntity firstBatch = invoiceBatchRepository.saveAndFlush(
                new InvoiceBatchEntity("batch-001", BatchTriggerSource.SCHEDULED, 1, BASE_TIME)
        );
        InvoiceBatchEntity secondBatch = invoiceBatchRepository.saveAndFlush(
                new InvoiceBatchEntity("batch-002", BatchTriggerSource.SCHEDULED, 2, BASE_TIME.plusSeconds(60))
        );

        secondBatch.markSucceeded(1);
        invoiceBatchRepository.saveAndFlush(secondBatch);

        InvoiceRecordEntity invoice = invoiceRecordRepository.saveAndFlush(new InvoiceRecordEntity(
                "inv_001",
                firstBatch.getBatchId(),
                100_00L,
                "Ada Lovelace",
                "01234567890",
                InvoiceStatus.CREATED,
                null,
                BASE_TIME.plusSeconds(120)
        ));
        invoice.markPaid(150L);
        invoiceRecordRepository.saveAndFlush(invoice);

        WebhookEventRecordEntity event = webhookEventRecordRepository.saveAndFlush(new WebhookEventRecordEntity(
                "evt_001",
                "invoice",
                invoice.getStarkInvoiceId(),
                "log_001",
                "paid",
                payload("evt_001", invoice.getStarkInvoiceId()),
                BASE_TIME.plusSeconds(180)
        ));
        event.markProcessed();
        webhookEventRecordRepository.saveAndFlush(event);

        TransferRecordEntity transfer = transferRecordRepository.saveAndFlush(new TransferRecordEntity(
                invoice.getStarkInvoiceId(),
                event.getStarkEventId(),
                "transfer-ext-001",
                100_00L,
                150L,
                98_50L,
                BASE_TIME.plusSeconds(240)
        ));
        transfer.markSucceeded("trf_001");
        transferRecordRepository.saveAndFlush(transfer);

        assertThat(invoiceBatchRepository.countByTriggerSource(BatchTriggerSource.SCHEDULED)).isEqualTo(2);
        assertThat(invoiceBatchRepository.existsByTriggerSourceAndSequenceNumber(BatchTriggerSource.SCHEDULED, 2))
                .isTrue();
        assertThat(invoiceBatchRepository.findTopByTriggerSourceOrderBySequenceNumberDesc(BatchTriggerSource.SCHEDULED))
                .hasValueSatisfying(batch -> assertThat(batch.getBatchId()).isEqualTo("batch-002"));
        assertThat(invoiceBatchRepository.findTop50ByOrderByStartedAtDesc())
                .extracting(InvoiceBatchEntity::getBatchId)
                .containsExactly("batch-002", "batch-001");

        assertThat(secondBatch.getStatus()).isEqualTo(BatchStatus.SUCCEEDED);
        assertThat(invoiceRecordRepository.findByStarkInvoiceId("inv_001"))
                .hasValueSatisfying(found -> {
                    assertThat(found.getBatchId()).isEqualTo("batch-001");
                    assertThat(found.getAmount()).isEqualTo(100_00L);
                    assertThat(found.getFeeAmount()).isEqualTo(150L);
                    assertThat(found.getStatus()).isEqualTo(InvoiceStatus.PAID);
                });
        assertThat(invoiceRecordRepository.findTop50ByOrderByCreatedAtDesc())
                .extracting(InvoiceRecordEntity::getStarkInvoiceId)
                .containsExactly("inv_001");

        assertThat(webhookEventRecordRepository.existsByStarkEventId("evt_001")).isTrue();
        assertThat(webhookEventRecordRepository.findByStarkEventId("evt_001"))
                .hasValueSatisfying(found -> {
                    assertThat(found.getStatus()).isEqualTo(WebhookProcessingStatus.PROCESSED);
                    assertThat(found.getRawPayload().get("eventId").asText()).isEqualTo("evt_001");
                    assertThat(found.getRawPayload().get("invoiceId").asText()).isEqualTo("inv_001");
                });

        assertThat(transferRecordRepository.existsByExternalId("transfer-ext-001")).isTrue();
        assertThat(transferRecordRepository.existsByInvoiceId("inv_001")).isTrue();
        assertThat(transferRecordRepository.findTop50ByOrderByCreatedAtDesc())
                .singleElement()
                .satisfies(found -> {
                    assertThat(found.getEventId()).isEqualTo("evt_001");
                    assertThat(found.getStatus()).isEqualTo(TransferStatus.SUCCEEDED);
                    assertThat(found.getStarkTransferId()).isEqualTo("trf_001");
                });
    }

    @Test
    void shouldRejectDuplicatedWebhookEventId() {
        webhookEventRecordRepository.saveAndFlush(new WebhookEventRecordEntity(
                "evt_duplicate",
                "invoice",
                "inv_duplicate",
                "log_001",
                "paid",
                payload("evt_duplicate", "inv_duplicate"),
                BASE_TIME
        ));

        assertThatThrownBy(() -> webhookEventRecordRepository.saveAndFlush(new WebhookEventRecordEntity(
                "evt_duplicate",
                "invoice",
                "inv_duplicate_2",
                "log_002",
                "paid",
                payload("evt_duplicate", "inv_duplicate_2"),
                BASE_TIME.plusSeconds(1)
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectDuplicatedTransferExternalId() {
        transferRecordRepository.saveAndFlush(new TransferRecordEntity(
                "inv_transfer_001",
                "evt_transfer_001",
                "external_duplicate",
                100_00L,
                0L,
                100_00L,
                BASE_TIME
        ));

        assertThatThrownBy(() -> transferRecordRepository.saveAndFlush(new TransferRecordEntity(
                "inv_transfer_002",
                "evt_transfer_002",
                "external_duplicate",
                100_00L,
                0L,
                100_00L,
                BASE_TIME.plusSeconds(1)
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectDuplicatedTransferInvoiceId() {
        transferRecordRepository.saveAndFlush(new TransferRecordEntity(
                "inv_transfer_duplicate",
                "evt_transfer_001",
                "external_001",
                100_00L,
                0L,
                100_00L,
                BASE_TIME
        ));

        assertThatThrownBy(() -> transferRecordRepository.saveAndFlush(new TransferRecordEntity(
                "inv_transfer_duplicate",
                "evt_transfer_002",
                "external_002",
                100_00L,
                0L,
                100_00L,
                BASE_TIME.plusSeconds(1)
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectScheduledBatchWithoutSequenceNumber() {
        assertThatThrownBy(() -> invoiceBatchRepository.saveAndFlush(
                new InvoiceBatchEntity("batch-invalid", BatchTriggerSource.SCHEDULED, null, BASE_TIME)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static JsonNode payload(String eventId, String invoiceId) {
        return OBJECT_MAPPER.createObjectNode()
                .put("eventId", eventId)
                .put("invoiceId", invoiceId)
                .put("subscription", "invoice");
    }
}
