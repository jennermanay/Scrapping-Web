package pe.edu.monitoreo;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Economics {
    private Economics() {}
    public record Result(BigDecimal difference, BigDecimal currentProfit, BigDecimal simulatedProfit,
                         BigDecimal currentMargin, BigDecimal simulatedMargin, boolean lower, boolean loss) {}
    public static Result compare(BigDecimal cost, BigDecimal own, BigDecimal external) {
        for (var value : new BigDecimal[]{cost, own, external})
            if (value == null || value.signum() <= 0) throw new IllegalArgumentException("Los importes deben ser mayores que cero.");
        var actual = own.subtract(cost);
        var simulated = external.subtract(cost);
        return new Result(external.subtract(own), actual, simulated, margin(actual, own), margin(simulated, external), external.compareTo(own) < 0, external.compareTo(cost) < 0);
    }
    private static BigDecimal margin(BigDecimal profit, BigDecimal price) {
        return profit.multiply(new BigDecimal("100")).divide(price, 2, RoundingMode.HALF_UP);
    }
}
