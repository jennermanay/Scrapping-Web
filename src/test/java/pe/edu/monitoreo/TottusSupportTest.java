package pe.edu.monitoreo;

import com.google.gson.*;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TottusSupportTest {
    static final String TITLE="Leche Gloria Reconstituida Entera Sixpack Lata 390 g";
    static final String URL="https://www.tottus.com.pe/tottus-pe/articulo/129087924/leche-gloria-reconstituida-entera-sixpack-lata-390-g/129087925";
    static JsonObject product(){return JsonParser.parseString("{\"name\":\""+TITLE+"\",\"isOutOfStock\":false,\"primaryVariantId\":\"129087925\",\"currentVariant\":\"129087925\",\"variants\":[{\"id\":\"129087925\",\"isPurchaseable\":true,\"isOnlineSellable\":true,\"prices\":[{\"symbol\":\"S/ \",\"crossed\":false,\"type\":\"internetPrice\",\"price\":[\"21.50\"]},{\"symbol\":\"S/ \",\"crossed\":true,\"type\":\"normalPrice\",\"price\":[\"24.60\"]},{\"symbol\":\"S/ \",\"crossed\":false,\"type\":\"cmrPrice\",\"price\":[\"18.00\"]}],\"offerings\":[{\"sellerName\":\"TOTTUS\",\"isActive\":true}]}]}").getAsJsonObject();}
    static String html(JsonObject product){return "<h1>"+TITLE+"</h1><script type='application/ld+json'>{\"@type\":\"Product\",\"name\":\""+TITLE+"\",\"offers\":[{\"priceCurrency\":\"PEN\",\"price\":21.50},{\"priceCurrency\":\"PEN\",\"price\":24.60}]}</script><script type='application/json' id='__NEXT_DATA__'>{\"props\":{\"pageProps\":{\"productData\":"+product+"}}}</script>";}
    static Store.Product own(){return new Store.Product(1,"PV-20402655","Leche Reconstituida Entera GLORIA Lata 390g Paquete 6un","GLORIA","Entera","Lata","390 g · 6 un","Lácteos","gloria,leche",new BigDecimal("18"),new BigDecimal("25.50"),true);}
    @Test void usesInternetPriceInsteadOfCrossedOrCardPrice(){var quote=new Extractor().auto(Jsoup.parse(html(product()),URL),URL);assertEquals(new BigDecimal("21.50"),quote.price());assertTrue(quote.evidence().contains("internetPrice"));assertTrue(quote.evidence().contains("129087925"));}
    @Test void doesNotInterpretForeignNextStateAsTottus(){assertTrue(TottusSupport.quote(Jsoup.parse(html(product()),"https://otra.example/p"),"https://otra.example/p").isEmpty());}
    @Test void rejectsUnavailableSkuAndMismatchedUrl(){var p=product();p.getAsJsonArray("variants").get(0).getAsJsonObject().addProperty("isPurchaseable",false);assertThrows(IllegalArgumentException.class,()->new Extractor().auto(Jsoup.parse(html(p),URL),URL));assertThrows(IllegalArgumentException.class,()->new Extractor().auto(Jsoup.parse(html(product()),URL+"0"),URL+"0"));}
    @Test void rejectsAmbiguousPriceAndUnknownCurrency(){var p=product();var price=p.getAsJsonArray("variants").get(0).getAsJsonObject().getAsJsonArray("prices").get(0).getAsJsonObject();price.addProperty("symbol","USD");assertThrows(IllegalArgumentException.class,()->new Extractor().auto(Jsoup.parse(html(p),URL),URL));price.addProperty("symbol","S/ ");price.getAsJsonArray("price").add("25.50");assertThrows(IllegalArgumentException.class,()->new Extractor().auto(Jsoup.parse(html(p),URL),URL));}
    @Test void packCapacityIsEquivalentButSingleCanAndTripackAreNot(){assertEquals(100,Equivalence.assess(own(),TITLE).confidence());assertTrue(Equivalence.assess(own(),"Leche Gloria Reconstituida Entera Lata 390g").confidence()<100);assertTrue(Equivalence.assess(own(),"Leche Gloria Reconstituida Entera Tripack Lata 390 g").confidence()<100);}
    @Test void discoversNttSearchAndPublicStateProductLinks()throws Exception {
        var requested=new ArrayList<String>();var root="https://www.tottus.com.pe/";
        var search=new SearchService(url->{requested.add(url);String path=URI.create(url).getPath();String content;
            if(path.equals("/"))content="<input id='testId-SearchBar-Input'><a href='/tottus-pe/buscar?Ntt=ninja&amp;sid=banner'>Promoción</a>";
            else if(path.equals("/tottus-pe/buscar"))content="<script id='__NEXT_DATA__' type='application/json'>{\"props\":{\"pageProps\":{\"results\":[{\"url\":\""+URL.substring(0,URL.lastIndexOf('/'))+"\",\"skuId\":\"129087925\",\"displayName\":\""+TITLE+"\"}]}}}</script>";
            else content=html(product());return Jsoup.parse(content,url);
        },14,8);
        var result=search.search(own(),root);assertEquals(1,result.quotes().size());assertEquals(new BigDecimal("21.50"),result.quotes().getFirst().price());assertEquals(URL,result.quotes().getFirst().url());assertTrue(result.searchUrls().stream().allMatch(u->u.contains("/tottus-pe/buscar?Ntt=")));assertTrue(requested.stream().anyMatch(u->u.equals(URL)));
    }
}
