package br.com.leandrotavares.starkbanktrial.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.TransferRecordEntity;

public interface TransferRecordRepository extends JpaRepository<TransferRecordEntity, Long> {

    boolean existsByExternalId(String externalId);

    boolean existsByInvoiceId(String invoiceId);

    List<TransferRecordEntity> findTop50ByOrderByCreatedAtDesc();
}
