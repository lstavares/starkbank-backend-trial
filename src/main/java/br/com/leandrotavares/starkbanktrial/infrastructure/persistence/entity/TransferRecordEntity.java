package br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.Objects;

import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.TransferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transfer_records")
public class TransferRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false, unique = true, length = 80)
    private String invoiceId;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "stark_transfer_id", length = 80)
    private String starkTransferId;

    @Column(name = "external_id", nullable = false, unique = true, length = 120)
    private String externalId;

    @Column(name = "gross_amount", nullable = false)
    private Long grossAmount;

    @Column(name = "fee_amount", nullable = false)
    private Long feeAmount;

    @Column(name = "net_amount", nullable = false)
    private Long netAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransferStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "error_message")
    private String errorMessage;

    protected TransferRecordEntity() {
    }

    public TransferRecordEntity(
            String invoiceId,
            String eventId,
            String externalId,
            Long grossAmount,
            Long feeAmount,
            Long netAmount,
            Instant createdAt
    ) {
        this.invoiceId = Objects.requireNonNull(invoiceId, "invoiceId must not be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.externalId = Objects.requireNonNull(externalId, "externalId must not be null");
        this.grossAmount = Objects.requireNonNull(grossAmount, "grossAmount must not be null");
        this.feeAmount = Objects.requireNonNull(feeAmount, "feeAmount must not be null");
        this.netAmount = Objects.requireNonNull(netAmount, "netAmount must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.status = TransferStatus.CREATED;
    }

    public void markSucceeded(String starkTransferId) {
        this.status = TransferStatus.SUCCEEDED;
        this.starkTransferId = starkTransferId;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = TransferStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return id;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getStarkTransferId() {
        return starkTransferId;
    }

    public String getExternalId() {
        return externalId;
    }

    public Long getGrossAmount() {
        return grossAmount;
    }

    public Long getFeeAmount() {
        return feeAmount;
    }

    public Long getNetAmount() {
        return netAmount;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
