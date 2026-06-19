CREATE TABLE invoice_batches (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(80) NOT NULL,
    trigger_source VARCHAR(30) NOT NULL,
    sequence_number INTEGER,
    invoice_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    error_message TEXT,
    CONSTRAINT uk_invoice_batches_batch_id UNIQUE (batch_id),
    CONSTRAINT ck_invoice_batches_trigger_source CHECK (trigger_source IN ('SCHEDULED', 'MANUAL')),
    CONSTRAINT ck_invoice_batches_status CHECK (status IN ('STARTED', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_invoice_batches_invoice_count CHECK (invoice_count >= 0),
    CONSTRAINT ck_invoice_batches_sequence_number CHECK (
        (trigger_source = 'SCHEDULED' AND sequence_number IS NOT NULL AND sequence_number > 0)
        OR (trigger_source = 'MANUAL' AND sequence_number IS NULL)
    )
);

CREATE UNIQUE INDEX uk_invoice_batches_scheduled_sequence
    ON invoice_batches (sequence_number)
    WHERE trigger_source = 'SCHEDULED';

CREATE INDEX idx_invoice_batches_status ON invoice_batches (status);
CREATE INDEX idx_invoice_batches_started_at ON invoice_batches (started_at);

CREATE TABLE invoice_records (
    id BIGSERIAL PRIMARY KEY,
    stark_invoice_id VARCHAR(80),
    batch_id BIGINT NOT NULL,
    amount INTEGER NOT NULL,
    name VARCHAR(160) NOT NULL,
    tax_id VARCHAR(32) NOT NULL,
    status VARCHAR(30) NOT NULL,
    fee_amount INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    CONSTRAINT fk_invoice_records_batch
        FOREIGN KEY (batch_id) REFERENCES invoice_batches (id),
    CONSTRAINT uk_invoice_records_stark_invoice_id UNIQUE (stark_invoice_id),
    CONSTRAINT ck_invoice_records_amount CHECK (amount > 0),
    CONSTRAINT ck_invoice_records_fee_amount CHECK (fee_amount IS NULL OR fee_amount >= 0)
);

CREATE INDEX idx_invoice_records_batch_id ON invoice_records (batch_id);
CREATE INDEX idx_invoice_records_status ON invoice_records (status);
CREATE INDEX idx_invoice_records_created_at ON invoice_records (created_at);

CREATE TABLE webhook_event_records (
    id BIGSERIAL PRIMARY KEY,
    stark_event_id VARCHAR(80) NOT NULL,
    subscription VARCHAR(80) NOT NULL,
    invoice_id VARCHAR(80),
    invoice_log_id VARCHAR(80),
    log_type VARCHAR(80),
    raw_payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    error_message TEXT,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    CONSTRAINT uk_webhook_event_records_stark_event_id UNIQUE (stark_event_id),
    CONSTRAINT ck_webhook_event_records_status CHECK (status IN ('RECEIVED', 'PROCESSED', 'SKIPPED', 'FAILED'))
);

CREATE INDEX idx_webhook_event_records_invoice_id ON webhook_event_records (invoice_id);
CREATE INDEX idx_webhook_event_records_status ON webhook_event_records (status);
CREATE INDEX idx_webhook_event_records_received_at ON webhook_event_records (received_at);

CREATE TABLE transfer_records (
    id BIGSERIAL PRIMARY KEY,
    invoice_id VARCHAR(80) NOT NULL,
    event_id BIGINT NOT NULL,
    stark_transfer_id VARCHAR(80),
    external_id VARCHAR(120) NOT NULL,
    gross_amount INTEGER NOT NULL,
    fee_amount INTEGER NOT NULL DEFAULT 0,
    net_amount INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    error_message TEXT,
    CONSTRAINT fk_transfer_records_event
        FOREIGN KEY (event_id) REFERENCES webhook_event_records (id),
    CONSTRAINT uk_transfer_records_invoice_id UNIQUE (invoice_id),
    CONSTRAINT uk_transfer_records_external_id UNIQUE (external_id),
    CONSTRAINT ck_transfer_records_gross_amount CHECK (gross_amount > 0),
    CONSTRAINT ck_transfer_records_fee_amount CHECK (fee_amount >= 0),
    CONSTRAINT ck_transfer_records_net_amount CHECK (net_amount > 0),
    CONSTRAINT ck_transfer_records_status CHECK (status IN ('CREATED', 'SUCCEEDED', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_transfer_records_event_id ON transfer_records (event_id);
CREATE INDEX idx_transfer_records_status ON transfer_records (status);
CREATE INDEX idx_transfer_records_created_at ON transfer_records (created_at);
