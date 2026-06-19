package br.com.leandrotavares.starkbanktrial.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.InvoiceRecordEntity;

public interface InvoiceRecordRepository extends JpaRepository<InvoiceRecordEntity, Long> {

    Optional<InvoiceRecordEntity> findByStarkInvoiceId(String starkInvoiceId);

    List<InvoiceRecordEntity> findTop50ByOrderByCreatedAtDesc();
}
