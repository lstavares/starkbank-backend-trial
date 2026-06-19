package br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.Objects;

import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.InvoiceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "invoice_records")
public class InvoiceRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stark_invoice_id", unique = true, length = 80)
    private String starkInvoiceId;

    @Column(name = "batch_id", nullable = false, length = 80)
    private String batchId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "tax_id", nullable = false, length = 32)
    private String taxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InvoiceStatus status;

    @Column(name = "fee_amount")
    private Long feeAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected InvoiceRecordEntity() {
    }

    public InvoiceRecordEntity(
            String starkInvoiceId,
            String batchId,
            Long amount,
            String name,
            String taxId,
            InvoiceStatus status,
            Long feeAmount,
            Instant createdAt
    ) {
        this.starkInvoiceId = starkInvoiceId;
        this.batchId = Objects.requireNonNull(batchId, "batchId must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.taxId = Objects.requireNonNull(taxId, "taxId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.feeAmount = feeAmount;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public void markPaid(Long feeAmount) {
        this.status = InvoiceStatus.PAID;
        this.feeAmount = feeAmount;
        this.updatedAt = Instant.now();
    }

    public void markFailed() {
        this.status = InvoiceStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getStarkInvoiceId() {
        return starkInvoiceId;
    }

    public String getBatchId() {
        return batchId;
    }

    public Long getAmount() {
        return amount;
    }

    public String getName() {
        return name;
    }

    public String getTaxId() {
        return taxId;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public Long getFeeAmount() {
        return feeAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
