package pe.edu.monitoreo;
import com.google.gson.*;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class WongSupportTest {
 static final String URL="https://www.wong.pe/sixpack-gloria-390g/p";
 static JsonObject state(){return JsonParser.parseString("""
 {"Product:milk":{"__typename":"Product","productName":"Sixpack Leche Gloria 390g","linkText":"sixpack-gloria-390g","items":[{"type":"id","id":"sku"}]},
 "sku":{"itemId":"123","sellers":[{"type":"id","id":"seller"}]},
 "seller":{"sellerId":"1","commertialOffer":{"type":"id","id":"offer"}},
 "offer":{"Price":24.60,"ListPrice":26,"AvailableQuantity":5,"teasers":[{"price":23.12}]}}
 """).getAsJsonObject();}
 static String html(JsonObject s){return "<script>var culture={\"currency\":\"PEN\"};</script><script>"+s+"</script>";}
 @Test void selectsOnlinePriceNotCardPromotion(){var q=new Extractor().auto(Jsoup.parse(html(state()),URL),URL);assertEquals(new BigDecimal("24.60"),q.price());assertTrue(q.evidence().contains("SKU=123"));}
 @Test void rejectsUnavailableAndMissingReference(){var s=state();s.getAsJsonObject("offer").addProperty("AvailableQuantity",0);assertThrows(IllegalArgumentException.class,()->WongSupport.quote(Jsoup.parse(html(s),URL),URL));s.remove("sku");assertThrows(IllegalArgumentException.class,()->WongSupport.quote(Jsoup.parse(html(s),URL),URL));}
 @Test void rejectsCurrencyAndMultipleSellers(){var s=state();assertThrows(IllegalArgumentException.class,()->WongSupport.quote(Jsoup.parse(html(s).replace("PEN","USD"),URL),URL));s.getAsJsonObject("sku").getAsJsonArray("sellers").add(s.getAsJsonObject("sku").getAsJsonArray("sellers").get(0));assertThrows(IllegalArgumentException.class,()->WongSupport.quote(Jsoup.parse(html(s),URL),URL));}
 @Test void discoversSameSiteLinksAndSearch(){var d=Jsoup.parse(html(state())+"<script>wongio</script>","https://www.wong.pe/");assertEquals(URL,WongSupport.productLinks(d).getFirst().url());assertTrue(WongSupport.searchUrl(d,"leche gloria").contains("/leche%20gloria?_q=leche%20gloria&map=ft"));assertTrue(WongSupport.quote(Jsoup.parse(html(state()),"https://other.example/p"),"https://other.example/p").isEmpty());}

 @Test void metroUsesSamePublicVtexIoFormat(){String address="https://www.metro.pe/sixpack-gloria-390g/p";var doc=Jsoup.parse(html(state())+"<script>metroio</script>",address);assertEquals(new BigDecimal("24.60"),new Extractor().auto(doc,address).price());assertTrue(WongSupport.searchUrl(doc,"agua cielo").contains("/agua%20cielo?_q="));assertTrue(WongSupport.productLinks(doc).getFirst().url().startsWith("https://www.metro.pe/"));}
 @Test @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="metro.live",matches="true") void liveMetroFindsWaterFromHomepage()throws Exception {
  var p=new Store.Product(0,"AGUA","Agua Sin Gas Cielo Bidón 7 L","Cielo","","Bidón","7 L","Aguas","",null,new BigDecimal("8.50"),true);
  var result=new SearchService().search(p,"https://www.metro.pe/");
  var water=result.quotes().stream().filter(q->q.url().contains("agua-sin-gas-cielo-bidon-7-l-286135/p")).findFirst().orElseThrow(()->new AssertionError(result.summary()+result.incidents()));
  assertTrue(water.price().signum()>0);System.out.println("METRO REAL: "+result.summary()+" | "+water.title()+" | PEN "+water.price());
 }
}
