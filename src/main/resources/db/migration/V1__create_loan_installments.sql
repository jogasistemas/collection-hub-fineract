CREATE TABLE loan_installments (
    id BIGSERIAL PRIMARY KEY,
    loan_id BIGINT NOT NULL,
    installment_number INTEGER NOT NULL,
    due_date DATE NOT NULL,
    principal_due NUMERIC(19,2) NOT NULL,
    interest_due NUMERIC(19,2) NOT NULL,
    total_due NUMERIC(19,2) NOT NULL,
    outstanding_amount NUMERIC(19,2) NOT NULL,
    paid BOOLEAN NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_loan_installment UNIQUE (loan_id, installment_number)
);
CREATE INDEX idx_loan_installments_loan_id ON loan_installments (loan_id);
