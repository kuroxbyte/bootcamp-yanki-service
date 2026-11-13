package dev.bootcamp.yankiservice.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Configuración de la mensajería: yanki-service es dueño de los tópicos de eventos de monederos y
 * de pedidos de operaciones sobre cuentas.
 */
@Configuration
public class MessagingConfig {

    /**
     * Tópico de eventos de monederos; la clave de cada mensaje es el id del monedero.
     */
    @Bean
    public NewTopic yankiEventsTopic(@Value("${messaging.topics.yanki-events}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    /**
     * Tópico de pedidos de cargo y abono a cuentas; la clave es el id de la cuenta.
     */
    @Bean
    public NewTopic yankiOperationsTopic(
            @Value("${messaging.topics.yanki-operations}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
