package br.com.leandrotavares.starkbanktrial.infrastructure.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.WebhookEventRecordEntity;

public interface WebhookEventRecordRepository extends JpaRepository<WebhookEventRecordEntity, Long> {

    boolean existsByStarkEventId(String starkEventId);

    Optional<WebhookEventRecordEntity> findByStarkEventId(String starkEventId);
}
