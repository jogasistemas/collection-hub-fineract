-- Conserva las cuotas existentes; el próximo sync actualiza importes reales desde Fineract.
ALTER TABLE loan_installments
    ADD COLUMN total_paid NUMERIC(19,2) NOT NULL DEFAULT 0;
