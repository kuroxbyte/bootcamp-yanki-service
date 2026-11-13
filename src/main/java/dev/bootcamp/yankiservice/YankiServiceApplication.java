package dev.bootcamp.yankiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio del monedero móvil Yanki: monederos, pagos entre celulares y asociación con
 * tarjetas de débito del banco.
 */
@SpringBootApplication
public class YankiServiceApplication {

    /**
     * Arranca el servicio.
     *
     * @param args argumentos de la línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(YankiServiceApplication.class, args);
    }
}
