package dev.bootcamp.yankiservice.domain.model.wallet;

import java.util.regex.Pattern;

/**
 * Tipo de documento de identidad del dueño de un monedero, con el formato de su número.
 */
public enum DocumentType {
    /**
     * Documento nacional de identidad: 8 dígitos.
     */
    DNI("\\d{8}"),
    /**
     * Carné de extranjería: hasta 12 caracteres alfanuméricos.
     */
    CEX("[A-Za-z0-9]{1,12}"),
    /**
     * Pasaporte: hasta 12 caracteres alfanuméricos.
     */
    PASSPORT("[A-Za-z0-9]{1,12}");

    private final Pattern format;

    DocumentType(String regex) {
        this.format = Pattern.compile(regex);
    }

    /**
     * Indica si el número tiene el formato de este tipo de documento.
     */
    public boolean accepts(String number) {
        return number != null && format.matcher(number).matches();
    }
}
