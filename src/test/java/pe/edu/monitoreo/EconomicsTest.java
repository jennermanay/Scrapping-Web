package pe.edu.monitoreo;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class EconomicsTest {
    private Economics.Result compare(String price) { return Economics.compare(new BigDecimal("80"),new BigDecimal("120"),new BigDecimal(price)); }
    @Test void lowerPrice() { var r=compare("105"); assertEquals(new BigDecimal("23.81"),r.simulatedMargin()); assertEquals(0,new BigDecimal("25").compareTo(r.simulatedProfit())); assertTrue(r.lower()); assertFalse(r.loss()); }
    @Test void higherPrice() { assertFalse(compare("130").lower()); }
    @Test void loss() { var r=compare("70"); assertTrue(r.loss()); assertEquals(0,new BigDecimal("-10").compareTo(r.simulatedProfit())); }
    @Test void equalPrice() { assertFalse(compare("120").lower()); }
    @Test void invalidPrice() { assertThrows(IllegalArgumentException.class, () -> compare("0")); assertThrows(IllegalArgumentException.class, () -> compare("-1")); }
    @Test void monetaryFormat() { assertEquals(new BigDecimal("1299.90"),PriceParser.parse("S/ 1,299.90")); }
    @Test void rejectAmbiguity() { for(String text:new String[]{"S/ 10 - S/ 20","1.299,90","gratis","0","1,20"}) assertThrows(IllegalArgumentException.class,() -> PriceParser.parse(text)); }
}
