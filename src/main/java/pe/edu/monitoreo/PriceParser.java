package pe.edu.monitoreo;

import java.math.BigDecimal;

public final class PriceParser {
    private PriceParser() {}
    public static BigDecimal parse(String text) {
        // PEN: 1,299.90 o 1299.90. Se rechazan rangos y formatos ambiguos.
        String value = text.replace('\u00a0', ' ').trim().replaceFirst("(?i)^(S/\\.?|PEN)\\s*", "").trim();
        if (!value.matches("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]{1,2})?"))
            throw new IllegalArgumentException("Precio no reconocido: " + text);
        var amount = new BigDecimal(value.replace(",", ""));
        if (amount.signum() <= 0 || amount.precision() - amount.scale() > 10)
            throw new IllegalArgumentException("Precio fuera del rango permitido.");
        return amount.setScale(2);
    }
}
