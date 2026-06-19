ALTER TABLE invoice_records DROP CONSTRAINT IF EXISTS fk_invoice_records_batch;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'invoice_records'
          AND column_name = 'batch_id'
          AND data_type IN ('integer', 'bigint')
    ) THEN
        ALTER TABLE invoice_records ADD COLUMN IF NOT EXISTS batch_id_logical VARCHAR(80);

        UPDATE invoice_records AS invoice
        SET batch_id_logical = batch.batch_id
        FROM invoice_batches AS batch
        WHERE invoice.batch_id = batch.id;

        UPDATE invoice_records
        SET batch_id_logical = batch_id::text
        WHERE batch_id_logical IS NULL;

        ALTER TABLE invoice_records ALTER COLUMN batch_id_logical SET NOT NULL;
        DROP INDEX IF EXISTS idx_invoice_records_batch_id;
        ALTER TABLE invoice_records DROP COLUMN batch_id;
        ALTER TABLE invoice_records RENAME COLUMN batch_id_logical TO batch_id;
    ELSE
        ALTER TABLE invoice_records ALTER COLUMN batch_id TYPE VARCHAR(80) USING batch_id::text;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_invoice_records_batch_id ON invoice_records (batch_id);

ALTER TABLE transfer_records DROP CONSTRAINT IF EXISTS fk_transfer_records_event;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'transfer_records'
          AND column_name = 'event_id'
          AND data_type IN ('integer', 'bigint')
    ) THEN
        ALTER TABLE transfer_records ADD COLUMN IF NOT EXISTS event_id_external VARCHAR(100);

        UPDATE transfer_records AS transfer
        SET event_id_external = event.stark_event_id
        FROM webhook_event_records AS event
        WHERE transfer.event_id = event.id;

        UPDATE transfer_records
        SET event_id_external = event_id::text
        WHERE event_id_external IS NULL;

        ALTER TABLE transfer_records ALTER COLUMN event_id_external SET NOT NULL;
        DROP INDEX IF EXISTS idx_transfer_records_event_id;
        ALTER TABLE transfer_records DROP COLUMN event_id;
        ALTER TABLE transfer_records RENAME COLUMN event_id_external TO event_id;
    ELSE
        ALTER TABLE transfer_records ALTER COLUMN event_id TYPE VARCHAR(100) USING event_id::text;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_transfer_records_event_id ON transfer_records (event_id);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'invoice_records'
          AND column_name = 'amount'
          AND data_type = 'integer'
    ) THEN
        ALTER TABLE invoice_records ALTER COLUMN amount TYPE BIGINT;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'invoice_records'
          AND column_name = 'fee_amount'
          AND data_type = 'integer'
    ) THEN
        ALTER TABLE invoice_records ALTER COLUMN fee_amount TYPE BIGINT;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'transfer_records'
          AND column_name = 'gross_amount'
          AND data_type = 'integer'
    ) THEN
        ALTER TABLE transfer_records ALTER COLUMN gross_amount TYPE BIGINT;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'transfer_records'
          AND column_name = 'fee_amount'
          AND data_type = 'integer'
    ) THEN
        ALTER TABLE transfer_records ALTER COLUMN fee_amount TYPE BIGINT;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'transfer_records'
          AND column_name = 'net_amount'
          AND data_type = 'integer'
    ) THEN
        ALTER TABLE transfer_records ALTER COLUMN net_amount TYPE BIGINT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_invoice_records_status'
          AND conrelid = 'invoice_records'::regclass
    ) THEN
        ALTER TABLE invoice_records
            ADD CONSTRAINT ck_invoice_records_status
            CHECK (status IN ('CREATED', 'PAID', 'FAILED', 'UNKNOWN'));
    END IF;
END $$;
