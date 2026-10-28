package pe.edu.monitoreo;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class ExtractorTest {
    private Store.Source source(String strategy){return new Store.Source(1,"Prueba","https://ejemplo.pe","h1",".precio","","",strategy,60,24,true);}
    @Test void css(){var q=new Extractor().parse(Jsoup.parse("<h1>Producto 1 L</h1><span class=precio>S/ 1,299.90</span>"),source("CSS"),"https://ejemplo.pe/p");assertEquals(new BigDecimal("1299.90"),q.price());}
    @Test void cssAttribute(){var s=new Store.Source(1,"Prueba","https://ejemplo.pe","h1","meta[itemprop=price]","","content","CSS",60,24,true);assertEquals(new BigDecimal("5.50"),new Extractor().parse(Jsoup.parse("<h1>Producto</h1><meta itemprop=price content=5.50>"),s,"https://ejemplo.pe/p").price());}
    @Test void ambiguousCss(){assertThrows(IllegalArgumentException.class,()->new Extractor().parse(Jsoup.parse("<h1>A</h1><b class=precio>10</b><b class=precio>20</b>"),source("CSS"),"https://ejemplo.pe"));}
    @Test void vtex(){var q=new Extractor().parse(Jsoup.parse("<script>var skuJson_0 = {\"name\":\"Leche 946ml\",\"available\":true,\"salesChannel\":\"1\",\"skus\":[{\"sku\":3533,\"available\":true,\"bestPrice\":610}]};</script>"),source("VTEX"),"https://ejemplo.pe/p");assertEquals(new BigDecimal("6.10"),q.price());assertTrue(q.evidence().contains("3533"));}
    @Test void outOfStock(){assertThrows(IllegalArgumentException.class,()->new Extractor().parse(Jsoup.parse("<script>var skuJson_0 = {\"name\":\"Leche\",\"available\":false,\"skus\":[{\"sku\":1,\"available\":false,\"bestPrice\":610}]};</script>"),source("VTEX"),"https://ejemplo.pe/p"));}
    @Test void jsonLd(){String json="{\"@graph\":[{\"@type\":\"Product\",\"name\":\"Leche\",\"offers\":{\"priceCurrency\":\"PEN\",\"price\":\"5.30\"}}]}";assertEquals(new BigDecimal("5.30"),new Extractor().parse(Jsoup.parse("<script type='application/ld+json'>"+json+"</script>"),source("JSON_LD"),"https://ejemplo.pe/p").price());}
    @Test void wrongCurrency(){assertThrows(IllegalArgumentException.class,()->new Extractor().parse(Jsoup.parse("<script type='application/ld+json'>{\"@type\":\"Product\",\"name\":\"Leche\",\"offers\":{\"priceCurrency\":\"USD\",\"price\":10}}</script>"),source("JSON_LD"),"https://ejemplo.pe"));}
    @Test void unsafeSchemes(){for(String url:new String[]{"file:///etc/passwd","javascript:alert(1)","https://u:p@ejemplo.pe"})assertThrows(IllegalArgumentException.class,()->Extractor.validateUrl(url,false));}
}
