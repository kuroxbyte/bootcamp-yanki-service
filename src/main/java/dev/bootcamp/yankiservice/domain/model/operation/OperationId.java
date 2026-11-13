package dev.bootcamp.yankiservice.domain.model.operation;

/**
 * Id de una operación pedida a transaction-service sobre una cuenta bancaria: dice a qué pago o
 * asociación pertenece y qué paso es, para aplicar la respuesta donde corresponde.
 *
 * <p>Formato: {@code payment:{id}:debit|credit|refund} o {@code link:{id}:transfer}.
 *
 * @param kind   {@code payment} o {@code link}
 * @param id     id del pago o de la asociación
 * @param action {@code debit}, {@code credit}, {@code refund} o {@code transfer}
 */
public record OperationId(String kind, String id, String action) {

    public static final String PAYMENT = "payment";
    public static final String LINK = "link";
    public static final String DEBIT = "debit";
    public static final String CREDIT = "credit";
    public static final String REFUND = "refund";
    public static final String TRANSFER = "transfer";

    /**
     * Operación de un paso de un pago.
     */
    public static OperationId payment(String paymentId, String action) {
        return new OperationId(PAYMENT, paymentId, action);
    }

    /**
     * Transferencia del saldo de un monedero al asociar una tarjeta.
     */
    public static OperationId linkTransfer(String linkId) {
        return new OperationId(LINK, linkId, TRANSFER);
    }

    /**
     * Lee un id de operación.
     *
     * @throws IllegalArgumentException si no tiene el formato esperado
     */
    public static OperationId parse(String value) {
        String[] parts = value.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Id de operación inválido: " + value);
        }
        return new OperationId(parts[0], parts[1], parts[2]);
    }

    @Override
    public String toString() {
        return kind + ":" + id + ":" + action;
    }
}
