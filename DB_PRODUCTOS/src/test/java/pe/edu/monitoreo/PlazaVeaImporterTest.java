package pe.edu.monitoreo;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import java.math.BigDecimal;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class PlazaVeaImporterTest {
    static final String HTML="<script>var skuJson_0 = {\"productId\":3649,\"name\":\"Leche Entera UHT GLORIA Caja 946ml\",\"available\":true,\"skus\":[{\"sku\":3533,\"ean\":\"7751271019545\",\"available\":true,\"bestPrice\":610}]}; dataLayer.push({\"productBrandName\":\"GLORIA\",\"productDepartmentName\":\"Lácteos\",\"productReferenceId\":\"358217\"});</script><table><tr><td class='value-field Presentacion-Empaque'>Caja</td></tr><tr><td class='value-field Contenido-Neto'>1L</td></tr><tr><td class='value-field Denominacion-Contenido-De-Grasa'>Entera</td></tr></table>";
    static PlazaVeaImporter.Preview preview(){return new PlazaVeaImporter().parse(Jsoup.parse(HTML),Samples.PLAZA_URL);}
    @Test void importsProductWithoutInventingCost(){var p=preview();assertEquals("PV-358217",p.code());assertEquals("GLORIA",p.brand());assertEquals("Caja",p.presentation());assertEquals("946 ml",p.capacity());assertEquals(new BigDecimal("6.10"),p.price());assertEquals("3533",p.sku());assertEquals("7751271019545",p.ean());assertFalse(p.warnings().isEmpty());}
    @Test void onlyAcceptsPlazaVeaProductUrls(){for(var url:new String[]{"https://www.plazavea.com.pe/categoria","https://plazavea.com.pe.ejemplo.org/producto/p","https://www.makro.plazavea.com.pe/producto/p","http://www.plazavea.com.pe/producto/p"})assertThrows(IllegalArgumentException.class,()->PlazaVeaImporter.validateUrl(url));}
    @Test void distinguishesDatabaseFailures(){assertTrue(DatabaseErrors.explain(new SQLException("detalle","28P01")).contains("rechaza"));assertTrue(DatabaseErrors.explain(new SQLException("detalle","42501")).contains("permisos"));assertTrue(DatabaseErrors.explain(new SQLException("detalle","3D000")).contains("no existe"));}
    @Test void confidenceRequiresMatchingCapacity(){var p=new Store.Product(1,"PV-1","Leche","GLORIA","Entera","Caja","1 L","Lácteos","",new BigDecimal("4"),new BigDecimal("7"),true);assertEquals(100,Equivalence.assess(p,"Leche Entera GLORIA Caja 1000ml").confidence());assertTrue(Equivalence.assess(p,"Leche Entera GLORIA Caja 500ml").confidence()<100);assertTrue(Equivalence.assess(p,"Leche Entera OtraMarca Caja 1L").confidence()<100);}
}
