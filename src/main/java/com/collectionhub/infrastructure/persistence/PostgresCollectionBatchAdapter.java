package com.collectionhub.infrastructure.persistence;
import com.collectionhub.domain.model.CollectionOrder;
import com.collectionhub.domain.port.out.CollectionBatchPort;
import java.sql.PreparedStatement;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
@Repository
public class PostgresCollectionBatchAdapter implements CollectionBatchPort {
    private final JdbcTemplate jdbc;
    public PostgresCollectionBatchAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    @Transactional
    public BatchResult save(
            LocalDate cutoff,
            LocalDate end,
            List<CollectionOrder> orders,
            List<Long> loanIds) {

        var keys = new GeneratedKeyHolder();

        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                INSERT INTO sync_batches (
                    cutoff_date,
                    horizon_end,
                    loans_processed,
                    orders_generated,
                    status
                ) VALUES (?, ?, ?, ?, ?)
                """, new String[]{"id"});

            ps.setDate(1, Date.valueOf(cutoff));
            ps.setDate(2, Date.valueOf(end));
            ps.setInt(3, loanIds.size());
            ps.setInt(4, orders.size());
            ps.setString(5, "COMPLETED");

            return ps;
        }, keys);

        long batchId = keys.getKey().longValue();

        // Registrar todos los préstamos procesados.
        for (Long loanId : loanIds) {
            jdbc.update("""
                INSERT INTO sync_batch_loans (
                    batch_id,
                    loan_id
                ) VALUES (?, ?)
                """,
                    batchId,
                    loanId
            );
        }

        // Registrar las órdenes con saldo pendiente.
        for (CollectionOrder o : orders) {
            jdbc.update("""
                INSERT INTO collection_orders (
                    batch_id,
                    loan_id,
                    client_id,
                    client_name,
                    installment_number,
                    due_date,
                    currency,
                    principal_outstanding,
                    interest_outstanding,
                    total_outstanding,
                    status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                    batchId,
                    o.loanId(),
                    o.clientId(),
                    o.clientName(),
                    o.installmentNumber(),
                    o.dueDate(),
                    o.currency(),
                    o.principalOutstanding(),
                    o.interestOutstanding(),
                    o.totalOutstanding(),
                    o.status()
            );
        }

        return new BatchResult(
                batchId,
                "COMPLETED",
                loanIds.size(),
                orders.size(),
                cutoff,
                end
        );
    }
    @Override
    public BatchResult findBatch(long id) {
        var rows = jdbc.query("SELECT id, status, loans_processed, orders_generated, cutoff_date, horizon_end FROM sync_batches WHERE id=?",
                (rs, i) -> new BatchResult(rs.getLong("id"), rs.getString("status"), rs.getInt("loans_processed"),
                        rs.getInt("orders_generated"), rs.getDate("cutoff_date").toLocalDate(),
                        rs.getDate("horizon_end").toLocalDate()), id);
        if (rows.isEmpty()) throw new CollectionBatchNotFoundException(id);
        return rows.getFirst();
    }
    @Override
    public List<CollectionOrder> findOrders(
            Long batchId,
            Long loanId,
            LocalDate dueBefore,
            String status) {

        StringBuilder sql = new StringBuilder("""
            SELECT co.*
            FROM collection_orders co
            WHERE 1 = 1
            """);

        List<Object> params = new java.util.ArrayList<>();

        if (batchId != null) {

            // Consulta histórica de un lote específico.
            sql.append(" AND co.batch_id = ?");
            params.add(batchId);

        } else {

            // Consultar únicamente el último lote de cada préstamo.
            sql.append("""
                AND co.batch_id = (
                    SELECT MAX(sbl.batch_id)
                    FROM sync_batch_loans sbl
                    WHERE sbl.loan_id = co.loan_id
                )
                """);
        }

        if (loanId != null) {
            sql.append(" AND co.loan_id = ?");
            params.add(loanId);
        }

        if (dueBefore != null) {
            sql.append(" AND co.due_date <= ?");
            params.add(dueBefore);
        }

        if (status != null) {
            sql.append(" AND co.status = ?");
            params.add(status);
        }

        sql.append(" ORDER BY co.due_date, co.loan_id, co.installment_number");

        return jdbc.query(
                sql.toString(),
                (rs, i) -> new CollectionOrder(
                        rs.getLong("id"),
                        rs.getLong("batch_id"),
                        rs.getLong("loan_id"),
                        rs.getObject("client_id", Long.class),
                        rs.getString("client_name"),
                        rs.getInt("installment_number"),
                        rs.getDate("due_date").toLocalDate(),
                        rs.getString("currency"),
                        rs.getBigDecimal("principal_outstanding"),
                        rs.getBigDecimal("interest_outstanding"),
                        rs.getBigDecimal("total_outstanding"),
                        rs.getString("status")
                ),
                params.toArray()
        );
    }
}
