CREATE TABLE sync_batches (
 id BIGSERIAL PRIMARY KEY,
 cutoff_date DATE NOT NULL,
 horizon_end DATE NOT NULL,
 loans_processed INTEGER NOT NULL,
 orders_generated INTEGER NOT NULL,
 status VARCHAR(20) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE collection_orders (
 id BIGSERIAL PRIMARY KEY,
 batch_id BIGINT NOT NULL REFERENCES sync_batches(id),
 loan_id BIGINT NOT NULL,
 client_id BIGINT,
 client_name VARCHAR(255),
 installment_number INTEGER NOT NULL,
 due_date DATE NOT NULL,
 currency VARCHAR(3) NOT NULL,
 principal_outstanding NUMERIC(19,2) NOT NULL,
 interest_outstanding NUMERIC(19,2) NOT NULL,
 total_outstanding NUMERIC(19,2) NOT NULL,
 status VARCHAR(20) NOT NULL,
 CONSTRAINT uk_batch_loan_installment UNIQUE(batch_id, loan_id, installment_number)
);
CREATE INDEX idx_collection_orders_loan_due ON collection_orders(loan_id, due_date);
CREATE INDEX idx_collection_orders_status_due ON collection_orders(status, due_date);
