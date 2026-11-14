package dev.bootcamp.yankiservice.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
// La prueba crea y migra los índices a mano: la creación automática y la migración en segundo
// plano al arrancar competirían con ella.
@DataMongoTest(properties = {
    "mongodb.index-migration.on-startup=false",
    "spring.data.mongodb.auto-index-creation=false"
})
@Import(IndexMigration.class)
class IndexMigrationTest {

    @Container @ServiceConnection static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired private IndexMigration migration;

    @Autowired private ReactiveMongoTemplate mongoTemplate;

    /**
     * Deja la colección como la creaba la versión anterior: índice único y no disperso.
     */
    @BeforeEach
    void createOldIndex() {
        mongoTemplate.dropCollection(WalletDocument.class).block();
        mongoTemplate.indexOps(WalletDocument.class)
                .createIndex(new Index("phone", Sort.Direction.ASC)
                        .named("phone")
                        .unique())
                .block();
    }

    private IndexInfo phoneIndex() {
        return mongoTemplate.indexOps(WalletDocument.class).getIndexInfo()
                .filter(index -> index.getName().equals("phone"))
                .blockFirst();
    }

    @Test
    void recreatesTheOutdatedIndexOnceAndKeepsItAfterwards() {
        assertThat(phoneIndex().isSparse()).isFalse();

        assertThat(migration.migrateAll().block()).isEqualTo(1L);

        IndexInfo index = phoneIndex();
        assertThat(index.isUnique()).isTrue();
        assertThat(index.isSparse()).isTrue();
        assertThat(migration.migrateAll().block()).isZero();
    }

    @Test
    void createsMissingIndexes() {
        mongoTemplate.dropCollection(WalletDocument.class).block();

        assertThat(migration.migrateAll().block()).isZero();

        assertThat(phoneIndex().isSparse()).isTrue();
        assertThat(mongoTemplate.indexOps(WalletDocument.class).getIndexInfo()
                .map(IndexInfo::getName).collectList().block())
                .contains("debitCardId");
    }

    @Test
    void startupMigrationCanBeDisabled() {
        migration.onApplicationReady();

        // Desactivada en esta prueba: el índice viejo sigue igual.
        assertThat(phoneIndex().isSparse()).isFalse();
    }
}
