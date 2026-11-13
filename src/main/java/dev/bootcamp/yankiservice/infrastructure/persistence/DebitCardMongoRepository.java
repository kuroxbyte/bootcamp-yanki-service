package dev.bootcamp.yankiservice.infrastructure.persistence;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

/**
 * Repositorio de Spring Data para {@link DebitCardDocument}.
 */
public interface DebitCardMongoRepository
        extends ReactiveMongoRepository<DebitCardDocument, String> {}
