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

    /**
     * Indica si el monedero fue dado de baja.
     */
    public boolean isClosed() {
        return status == WalletStatus.CLOSED;
    }

    /**
     * Valida los nuevos datos de contacto del monedero: el IMEI de otro celular y el correo. El
     * documento y el celular no cambian (el celular identifica al monedero).
     *
     * @return un monedero con los nuevos datos y el mismo id, saldo, tarjeta y estado
     * @throws BusinessRuleException si el IMEI o el correo no son válidos
     */
    public Wallet withContact(String newImei, String newEmail) {
        Wallet updated = new Wallet(documentType, documentNumber, phone, newImei, newEmail);
        updated.setId(id);
        updated.setBalance(balance);
        updated.setDebitCardId(debitCardId);
        updated.setStatus(status);
        updated.setCreatedAt(createdAt);
        return updated;
    }

    /**
     * Verifica que el monedero se pueda dar de baja: debe estar activo (no asociando una tarjeta)
     * y, si usa su propio saldo, tenerlo en 0. Con tarjeta, el dinero está en la cuenta y la
     * tarjeta queda libre.
     *
     * @throws BusinessRuleException si no se puede dar de baja
     */
    public void validateClosing() {
        if (status != WalletStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "El monedero está asociando una tarjeta; intente en unos segundos");
        }
        if (!hasDebitCard() && balance.signum() != 0) {
            throw new BusinessRuleException("El monedero tiene saldo "
                    + balance.toPlainString() + "; debe quedar en 0 para darlo de baja");
        }
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
