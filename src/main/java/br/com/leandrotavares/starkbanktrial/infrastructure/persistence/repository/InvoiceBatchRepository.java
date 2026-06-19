package br.com.leandrotavares.starkbanktrial.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.InvoiceBatchEntity;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.BatchTriggerSource;

public interface InvoiceBatchRepository extends JpaRepository<InvoiceBatchEntity, Long> {

    Optional<InvoiceBatchEntity> findByBatchId(String batchId);

    long countByTriggerSource(BatchTriggerSource triggerSource);

    boolean existsByTriggerSourceAndSequenceNumber(BatchTriggerSource triggerSource, Integer sequenceNumber);

    Optional<InvoiceBatchEntity> findTopByTriggerSourceOrderBySequenceNumberDesc(BatchTriggerSource triggerSource);

    List<InvoiceBatchEntity> findTop50ByOrderByStartedAtDesc();
}
