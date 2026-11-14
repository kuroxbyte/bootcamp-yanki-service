package dev.bootcamp.yankiservice.infrastructure.persistence;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.index.ReactiveIndexOperations;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Pone los índices de MongoDB al día con las anotaciones {@code @Indexed} de los documentos.
 *
 * <p>En este servicio es la única que crea los índices: la creación automática de Spring está
 * desactivada en config-repo porque no modifica un índice que ya existe con otras opciones (por
 * ejemplo, el índice único de {@code wallets.phone}, que ahora es disperso) y registraría un error.
 * Al arrancar, compara cada índice declarado con el existente del mismo nombre; si difieren en
 * {@code unique} o {@code sparse}, borra el viejo y crea el nuevo. Los índices que faltan se
 * crean.
 *
 * <p>Corre en segundo plano y nunca impide el arranque: un error se registra.
 */
@Slf4j
@Component
public class IndexMigration {

    private final ReactiveMongoTemplate mongoTemplate;
    private final MongoPersistentEntityIndexResolver resolver;
    private final boolean onStartup;

    /**
     * Crea la migración con el resolutor de índices del contexto de mapeo de MongoDB.
     *
     * @param onStartup si migra al arrancar ({@code mongodb.index-migration.on-startup}, true por
     *     defecto)
     */
    public IndexMigration(
            ReactiveMongoTemplate mongoTemplate,
            @Value("${mongodb.index-migration.on-startup:true}") boolean onStartup) {
        this.mongoTemplate = mongoTemplate;
        this.resolver = new MongoPersistentEntityIndexResolver(
                mongoTemplate.getConverter().getMappingContext());
        this.onStartup = onStartup;
    }

    /**
     * Migra los índices al terminar de arrancar la aplicación.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!onStartup) {
            return;
        }
        migrateAll().subscribe(
                migrated -> { },
                error -> log.error("Index migration failed", error));
    }

    /**
     * Migra los índices de todos los documentos.
     *
     * @return cuántos índices se recrearon con otras opciones
     */
    public Mono<Long> migrateAll() {
        return Flux.fromIterable(
                        mongoTemplate.getConverter().getMappingContext().getPersistentEntities())
                .filter(entity -> entity.isAnnotationPresent(
                        org.springframework.data.mongodb.core.mapping.Document.class))
                .concatMap(this::migrate)
                .reduce(0L, Long::sum);
    }

    private Mono<Long> migrate(MongoPersistentEntity<?> entity) {
        ReactiveIndexOperations operations = mongoTemplate.indexOps(entity.getType());
        List<IndexDefinition> declared = StreamSupport
                .stream(resolver.resolveIndexFor(entity.getType()).spliterator(), false)
                .map(IndexDefinition.class::cast)
                .toList();
        return operations.getIndexInfo().collectList()
                .flatMapMany(existing -> Flux.fromIterable(declared)
                        .concatMap(definition -> migrate(operations, existing, definition)))
                .reduce(0L, Long::sum);
    }

    private Mono<Long> migrate(
            ReactiveIndexOperations operations,
            List<IndexInfo> existing,
            IndexDefinition definition) {
        String name = definition.getIndexOptions().getString("name");
        boolean outdated = existing.stream()
                .anyMatch(index -> index.getName().equals(name) && !matches(index, definition));
        if (!outdated) {
            // Otro proceso pudo crear el índice viejo entre la lectura y la creación: se recrea.
            return operations.ensureIndex(definition).thenReturn(0L)
                    .onErrorResume(DataIntegrityViolationException.class,
                            conflict -> recreate(operations, name, definition));
        }
        return recreate(operations, name, definition);
    }

    private Mono<Long> recreate(
            ReactiveIndexOperations operations,
            String name,
            IndexDefinition definition) {
        log.warn("Recreating index {} with its new options {}", name,
                definition.getIndexOptions().toJson());
        return operations.dropIndex(name)
                .then(operations.ensureIndex(definition))
                .thenReturn(1L);
    }

    /**
     * Indica si el índice existente tiene las mismas opciones que el declarado.
     */
    static boolean matches(IndexInfo index, IndexDefinition definition) {
        Document options = definition.getIndexOptions();
        return index.isUnique() == options.getBoolean("unique", false)
                && index.isSparse() == options.getBoolean("sparse", false);
    }
}
