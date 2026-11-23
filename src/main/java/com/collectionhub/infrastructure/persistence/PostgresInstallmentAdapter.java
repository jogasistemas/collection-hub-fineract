package com.collectionhub.infrastructure.persistence;

import com.collectionhub.domain.model.Installment;
import com.collectionhub.domain.port.out.InstallmentRepositoryPort;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PostgresInstallmentAdapter implements InstallmentRepositoryPort {
    private final SpringDataInstallmentRepository repository;
    public PostgresInstallmentAdapter(SpringDataInstallmentRepository repository) { this.repository = repository; }

    @Override
    public void replaceForLoan(long loanId, List<Installment> installments) {
        List<InstallmentEntity> existing = repository.findByLoanIdOrderByInstallmentNumberAsc(loanId);
        Map<Integer, InstallmentEntity> byNumber = new HashMap<>();
        for (InstallmentEntity entity : existing) byNumber.put(entity.getInstallmentNumber(), entity);
        Instant now = Instant.now();
        for (Installment installment : installments) {
            InstallmentEntity entity = byNumber.remove(installment.installmentNumber());
            if (entity == null) entity = new InstallmentEntity(loanId, installment, now);
            else entity.update(installment, now);
            repository.save(entity);
        }
        // El cronograma completo de Fineract es la fuente de verdad.
        repository.deleteAll(byNumber.values());
        repository.flush();
    }

    @Override
    public List<Installment> findByLoanId(long loanId) {
        return repository.findByLoanIdOrderByInstallmentNumberAsc(loanId).stream()
                .map(InstallmentEntity::toDomain).toList();
    }
}
