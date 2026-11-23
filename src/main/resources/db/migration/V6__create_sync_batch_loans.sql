CREATE TABLE sync_batch_loans (
    batch_id BIGINT NOT NULL REFERENCES sync_batches(id),
    loan_id BIGINT NOT NULL,

    CONSTRAINT pk_sync_batch_loans
        PRIMARY KEY (batch_id, loan_id),

    CONSTRAINT ck_sync_batch_loans_loan_id
        CHECK (loan_id > 0)
);

CREATE INDEX idx_sync_batch_loans_loan_batch
    ON sync_batch_loans(loan_id, batch_id DESC);