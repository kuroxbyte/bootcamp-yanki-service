package dev.bootcamp.yankiservice.domain.model.wallet;

import dev.bootcamp.yankiservice.domain.exception.BusinessRuleException;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Monedero móvil Yanki. No hace falta ser cliente del banco: basta un documento de identidad, un
 * celular, el IMEI del celular y un correo. El celular identifica al monedero para enviar y recibir
 * pagos.
 *
 * <p>Sin tarjeta de débito, el monedero tiene su propio saldo. Con tarjeta, los pagos se cargan y
 * abonan en la cuenta principal de la tarjeta y el monedero no guarda saldo.
 */
@Getter
public class Wallet {
    private static final Pattern PHONE = Pattern.compile("9\\d{8}");
    private static final Pattern IMEI = Pattern.compile("\\d{15}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    @Setter private String id;
    private final DocumentType documentType;
    private final String documentNumber;
    private final String phone;
    private final String imei;
    private final String email;
    @Setter private BigDecimal balance;
    @Setter private String debitCardId;
    @Setter private WalletStatus status;
    @Setter private LocalDate createdAt;

    /**
     * Crea un monedero activo, sin tarjeta y con saldo 0, validando sus datos.
     *
     * @throws BusinessRuleException si algún dato no tiene el formato esperado
     */
    public Wallet(
            DocumentType documentType,
            String documentNumber,
            String phone,
            String imei,
            String email) {
        if (documentType == null || !documentType.accepts(documentNumber)) {
            throw new BusinessRuleException(
                    "El número de documento no tiene el formato de " + documentType);
        }
        if (phone == null || !PHONE.matcher(phone).matches()) {
            throw new BusinessRuleException(
                    "El celular debe tener 9 dígitos y empezar con 9");
        }
        if (imei == null || !IMEI.matcher(imei).matches() || !luhn(imei)) {
            throw new BusinessRuleException("El IMEI no es válido");
        }
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw new BusinessRuleException("El correo no es válido");
        }
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.phone = phone;
        this.imei = imei;
        this.email = email;
        this.balance = BigDecimal.ZERO;
        this.status = WalletStatus.ACTIVE;
    }

    /**
     * Indica si el monedero tiene asociada una tarjeta de débito.
     */
    public boolean hasDebitCard() {
        return debitCardId != null;
    }

    // Dígito de control de Luhn, el que usa el IMEI.
    private static boolean luhn(String digits) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int digit = digits.charAt(digits.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return sum % 10 == 0;
    }
}
