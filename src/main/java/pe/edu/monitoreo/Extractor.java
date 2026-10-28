package pe.edu.monitoreo;

import java.math.BigDecimal;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import org.jsoup.*;
import org.jsoup.nodes.*;
import com.google.gson.*;

/** Reads HTML only; never executes scripts or calls internal store APIs. */
public final class Extractor implements IExtractorFuente {
    public record Quote(String title,BigDecimal price,String url,String evidence) {}
    public static void validateUrl(String url,boolean demo) {
        if(demo&&url.equals("demo://producto"))return;
        try{var uri=URI.create(url);if(!Set.of("http","https").contains(Objects.toString(uri.getScheme(),""))||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException();}
        catch(Exception e){throw new IllegalArgumentException("Se requiere una URL HTTP/HTTPS válida sin credenciales.");}
    }
    @Override public Quote extract(Store.Source source,String url)throws Exception {
        validateUrl(url,source.strategy().equals("DEMO"));
        if(source.strategy().equals("DEMO")){
            if(!url.equals("demo://producto"))throw new IllegalArgumentException("La estrategia DEMO requiere demo://producto.");
            return parse(Jsoup.parse("<h1>Producto demostración 1 L</h1><span class='precio'>S/ 105.00</span>",url),source,url);
        }
        Document document=fetch(url);
        return parse(document,source,document.location());
    }
    Document fetch(String url)throws IOException {
        validateUrl(url,false);
        Document document=null;
        // One retry only for transient network failures and server errors. No retries for access restrictions.
        for(int attempt=0;attempt<2;attempt++)try {
            document=Jsoup.connect(url).userAgent("MonitoreoPrecios/0.2 (Java; lectura de precios publicos)")
                .timeout(10000).maxBodySize(4_000_000).get();break;
        }catch(HttpStatusException e){if(e.getStatusCode()<500||attempt==1)throw e;}
         catch(IOException e){if(attempt==1)throw e;}
        if(document==null)throw new IOException("Sin respuesta HTML.");
        var title=document.title().toLowerCase(Locale.ROOT);
        if(title.contains("captcha")||title.contains("access denied")||title.contains("just a moment"))throw new IOException("La fuente restringe el acceso automatizado. Desactiva y revisa la fuente.");
        return document;
    }
    public Quote parse(Document document,Store.Source source,String url) {
        return switch(source.strategy()) {
            case "AUTO" -> auto(document,url);
            case "CSS","DEMO" -> {
                String t=select(document,source.strategy().equals("DEMO")?"h1":source.titleSelector(),source.titleAttribute());
                String p=select(document,source.strategy().equals("DEMO")?".precio":source.priceSelector(),source.priceAttribute());
                yield quote(t,p,url,"CSS; título="+source.titleSelector()+"; precio="+source.priceSelector()+"; valor="+p);
            }
            case "VTEX" -> vtex(document,url);
            case "JSON_LD" -> jsonLd(document,url);
            default -> throw new IllegalArgumentException("Estrategia no soportada.");
        };
    }
    Quote auto(Document document,String url){
        var wong=WongSupport.quote(document,url);if(wong.isPresent())return wong.get();
        var tottus=TottusSupport.quote(document,url);if(tottus.isPresent())return tottus.get();
        if(document.select("script").stream().anyMatch(s->Pattern.compile("\\bvar\\s+skuJson(?:_\\d+)?\\s*=").matcher(s.data()).find()))return vtex(document,url);
        var products=new ArrayList<JsonObject>();
        for(var script:document.select("script[type=application/ld+json]"))try{collect(JsonParser.parseString(script.data()),products);}catch(JsonParseException ignored){}
        if(!products.isEmpty())return jsonLd(document,url);
        var title=document.select("h1");var prices=document.select("[itemprop=price],meta[property=product:price:amount]");
        if(title.size()!=1||prices.isEmpty())throw new IllegalArgumentException("La ficha no publica un título y precio reconocibles en HTML.");
        var amounts=new LinkedHashSet<String>();for(var price:prices){String raw=price.hasAttr("content")?price.attr("content"):price.text();amounts.add(PriceParser.parse(raw).toPlainString());}
        String currency=document.select("[itemprop=priceCurrency],meta[property=product:price:currency]").stream().map(e->e.hasAttr("content")?e.attr("content"):e.text()).filter(s->!s.isBlank()).distinct().collect(java.util.stream.Collectors.joining());
        if(!currency.equals("PEN"))throw new IllegalArgumentException("La ficha no permite confirmar la moneda PEN. Configura un extractor específico.");
        if(amounts.size()!=1)throw new IllegalArgumentException("La ficha publica varios precios; requiere revisión del extractor.");
        return quote(title.first().text(),amounts.iterator().next(),url,"HTML con microdatos de producto; moneda=PEN; detección automática");
    }
    private static String select(Document doc,String selector,String attribute){
        var nodes=doc.select(selector);if(nodes.size()!=1)throw new IllegalArgumentException("El selector debe encontrar exactamente un elemento: "+selector+" (encontrados "+nodes.size()+")");
        String value=attribute.isBlank()?nodes.first().text():nodes.first().attr(attribute);
        return Store.required(value,"Valor extraído por "+selector);
    }
    private static Quote quote(String title,String price,String url,String evidence){return new Quote(Store.required(title,"Título extraído"),PriceParser.parse(price),url,evidence);}
    private static Quote vtex(Document doc,String url){
        var product=vtexProduct(doc);var skus=product.getAsJsonArray("skus");
        if(skus==null||skus.size()!=1)throw new IllegalArgumentException("La ficha VTEX contiene varias variantes. Configura una ficha inequívoca.");
        var sku=skus.get(0).getAsJsonObject();
        if(!bool(product,"available")||!bool(sku,"available"))throw new IllegalArgumentException("El producto figura sin disponibilidad.");
        String price=sku.get("bestPrice").getAsBigDecimal().movePointLeft(2).toPlainString();
        return quote(product.get("name").getAsString(),price,url,"VTEX HTML sku="+sku.get("sku").getAsString()+"; bestPrice en céntimos="+sku.get("bestPrice")+"; canal="+value(product,"salesChannel")+"; verificar ubicación y oferta en la web");
    }
    static JsonObject vtexProduct(Document doc){
        var pattern=Pattern.compile("\\bvar\\s+skuJson(?:_\\d+)?\\s*=\\s*(\\{.*?\\})\\s*;",Pattern.DOTALL);
        var found=new ArrayList<JsonObject>();
        for(var script:doc.select("script")) {var m=pattern.matcher(script.data());while(m.find()){
            var product=JsonParser.parseString(m.group(1)).getAsJsonObject();var skus=product.getAsJsonArray("skus");if(skus==null)continue;
            found.add(product);
        }}
        if(found.size()!=1)throw new IllegalArgumentException("No se encontró un único objeto skuJson público. Revisa la fuente o sus restricciones.");return found.get(0);
    }
    private static boolean bool(JsonObject object,String field){return object.has(field)&&object.get(field).getAsBoolean();}
    private static String value(JsonObject object,String field){return object.has(field)?object.get(field).getAsString():"";}
    private static Quote jsonLd(Document doc,String url){
        var products=new ArrayList<JsonObject>();for(var script:doc.select("script[type=application/ld+json]"))collect(JsonParser.parseString(script.data()),products);
        if(products.size()!=1)throw new IllegalArgumentException("Se requiere un único Product JSON-LD, sin mezclar recomendaciones.");
        var product=products.get(0);var offers=product.get("offers");
        if(offers==null)throw new IllegalArgumentException("JSON-LD no contiene oferta.");
        if(offers.isJsonArray()){if(offers.getAsJsonArray().size()!=1)throw new IllegalArgumentException("Hay varias ofertas. Usa selectores específicos.");offers=offers.getAsJsonArray().get(0);}
        var offer=offers.getAsJsonObject();if(!value(offer,"priceCurrency").equals("PEN"))throw new IllegalArgumentException("La oferta debe declarar moneda PEN.");
        if(value(offer,"availability").toLowerCase(Locale.ROOT).contains("outofstock"))throw new IllegalArgumentException("Producto sin stock.");
        if(!offer.has("price"))throw new IllegalArgumentException("Oferta agregada o sin precio inequívoco.");
        return quote(product.get("name").getAsString(),offer.get("price").getAsString(),url,"JSON-LD Product.offers.price; moneda=PEN; revisar condición y ubicación");
    }
    private static void collect(JsonElement element,List<JsonObject> out){
        if(element.isJsonArray()){for(var item:element.getAsJsonArray())collect(item,out);return;}
        if(!element.isJsonObject())return;var object=element.getAsJsonObject();var type=object.get("@type");
        if(type!=null&&((type.isJsonPrimitive()&&type.getAsString().equals("Product"))||(type.isJsonArray()&&type.getAsJsonArray().asList().stream().anyMatch(t->t.isJsonPrimitive()&&t.getAsString().equals("Product")))))out.add(object);
        if(object.has("@graph"))collect(object.get("@graph"),out);
    }
}
