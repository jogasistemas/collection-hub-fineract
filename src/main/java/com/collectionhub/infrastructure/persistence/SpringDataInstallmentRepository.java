package com.collectionhub.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataInstallmentRepository extends JpaRepository<InstallmentEntity, Long> {
    List<InstallmentEntity> findByLoanIdOrderByInstallmentNumberAsc(Long loanId);
}
