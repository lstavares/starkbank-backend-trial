package br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.Objects;

import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.BatchStatus;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.BatchTriggerSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "invoice_batches")
public class InvoiceBatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false, unique = true, length = 80)
    private String batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, length = 30)
    private BatchTriggerSource triggerSource;

    @Column(name = "sequence_number")
    private Integer sequenceNumber;

    @Column(name = "invoice_count", nullable = false)
    private int invoiceCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BatchStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_message")
    private String errorMessage;

    protected InvoiceBatchEntity() {
    }

    public InvoiceBatchEntity(String batchId, BatchTriggerSource triggerSource, Integer sequenceNumber, Instant startedAt) {
        this.batchId = Objects.requireNonNull(batchId, "batchId must not be null");
        this.triggerSource = Objects.requireNonNull(triggerSource, "triggerSource must not be null");
        this.sequenceNumber = sequenceNumber;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.invoiceCount = 0;
        this.status = BatchStatus.STARTED;
    }

    public void markSucceeded(int invoiceCount) {
        this.invoiceCount = invoiceCount;
        this.status = BatchStatus.SUCCEEDED;
        this.finishedAt = Instant.now();
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = BatchStatus.FAILED;
        this.finishedAt = Instant.now();
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return id;
    }

    public String getBatchId() {
        return batchId;
    }

    public BatchTriggerSource getTriggerSource() {
        return triggerSource;
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public int getInvoiceCount() {
        return invoiceCount;
    }

    public BatchStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
