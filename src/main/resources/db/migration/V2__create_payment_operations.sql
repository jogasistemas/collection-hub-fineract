CREATE TABLE payment_operations (
 id BIGSERIAL PRIMARY KEY,
 idempotency_key VARCHAR(128) NOT NULL UNIQUE,
 loan_id BIGINT NOT NULL,
 amount NUMERIC(19,2) NOT NULL,
 payment_date DATE NOT NULL,
 status VARCHAR(20) NOT NULL,
 fineract_transaction_id BIGINT,
 failure_reason VARCHAR(500),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_payment_operations_loan_id ON payment_operations(loan_id);
