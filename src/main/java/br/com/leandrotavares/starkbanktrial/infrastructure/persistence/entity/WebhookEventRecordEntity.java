package br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.WebhookProcessingStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "webhook_event_records")
public class WebhookEventRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stark_event_id", nullable = false, unique = true, length = 80)
    private String starkEventId;

    @Column(name = "subscription", nullable = false, length = 80)
    private String subscription;

    @Column(name = "invoice_id", length = 80)
    private String invoiceId;

    @Column(name = "invoice_log_id", length = 80)
    private String invoiceLogId;

    @Column(name = "log_type", length = 80)
    private String logType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WebhookProcessingStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected WebhookEventRecordEntity() {
    }

    public WebhookEventRecordEntity(
            String starkEventId,
            String subscription,
            String invoiceId,
            String invoiceLogId,
            String logType,
            JsonNode rawPayload,
            Instant receivedAt
    ) {
        this.starkEventId = Objects.requireNonNull(starkEventId, "starkEventId must not be null");
        this.subscription = Objects.requireNonNull(subscription, "subscription must not be null");
        this.invoiceId = invoiceId;
        this.invoiceLogId = invoiceLogId;
        this.logType = logType;
        this.rawPayload = Objects.requireNonNull(rawPayload, "rawPayload must not be null");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        this.status = WebhookProcessingStatus.RECEIVED;
    }

    public void markProcessed() {
        this.status = WebhookProcessingStatus.PROCESSED;
        this.processedAt = Instant.now();
        this.errorMessage = null;
    }

    public void markSkipped(String reason) {
        this.status = WebhookProcessingStatus.SKIPPED;
        this.processedAt = Instant.now();
        this.errorMessage = reason;
    }

    public void markFailed(String errorMessage) {
        this.status = WebhookProcessingStatus.FAILED;
        this.processedAt = Instant.now();
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return id;
    }

    public String getStarkEventId() {
        return starkEventId;
    }

    public String getSubscription() {
        return subscription;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public String getInvoiceLogId() {
        return invoiceLogId;
    }

    public String getLogType() {
        return logType;
    }

    public JsonNode getRawPayload() {
        return rawPayload;
    }

    public WebhookProcessingStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
