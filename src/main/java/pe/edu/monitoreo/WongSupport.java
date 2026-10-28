package pe.edu.monitoreo;

import com.google.gson.*;
import org.jsoup.nodes.Document;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads Cencosud's public, normalized VTEX IO state without executing scripts. */
final class WongSupport {
    record ProductLink(String title,String url) {}
    private static boolean accepts(String url){try{return URI.create(url).getHost().replaceFirst("(?i)^www\\.","").matches("(?i)(wong|metro)\\.pe");}catch(Exception e){return false;}}
    static String searchUrl(Document doc,String query){
        if(!accepts(doc.location())||!(doc.outerHtml().contains("wongio")||doc.outerHtml().contains("metroio")))return "";
        var uri=URI.create(doc.location());String q=URLEncoder.encode(query,StandardCharsets.UTF_8).replace("+","%20");
        return uri.getScheme()+"://"+uri.getRawAuthority()+"/"+q+"?_q="+q+"&map=ft";
    }
    private static List<JsonObject> states(Document doc){
        var out=new ArrayList<JsonObject>();
        for(var script:doc.select("script"))try{String raw=script.data().strip();if(!raw.startsWith("{")||!raw.contains("\"Product:"))continue;var state=JsonParser.parseString(raw);if(state.isJsonObject())out.add(state.getAsJsonObject());}catch(JsonParseException ignored){}
        return out;
    }
    static List<ProductLink> productLinks(Document doc){
        if(!accepts(doc.location()))return List.of();var out=new ArrayList<ProductLink>();var origin=URI.create(doc.location());
        for(var state:states(doc))for(var entry:state.entrySet())if(entry.getValue().isJsonObject()){
            var p=entry.getValue().getAsJsonObject();if(!text(p,"__typename").equals("Product")||text(p,"linkText").isBlank())continue;
            String slug=text(p,"linkText");if(!slug.matches("[a-zA-Z0-9_-]+"))continue;
            out.add(new ProductLink(text(p,"productName"),origin.resolve("/"+slug+"/p").toString()));
        }return out;
    }
    static Optional<Extractor.Quote> quote(Document doc,String url){
        if(!accepts(url))return Optional.empty();String path=URI.create(url).getPath();if(!path.endsWith("/p"))return Optional.empty();
        for(var state:states(doc))for(var entry:state.entrySet())if(entry.getValue().isJsonObject()){
            var p=entry.getValue().getAsJsonObject();if(!text(p,"__typename").equals("Product")||!path.equals("/"+text(p,"linkText")+"/p"))continue;
            if(!doc.outerHtml().contains("\"currency\":\"PEN\""))throw new IllegalArgumentException("Cencosud no permite verificar moneda PEN.");
            var items=p.getAsJsonArray("items");if(items==null||items.size()!=1)throw new IllegalArgumentException("Cencosud publica varias variantes; requiere revisión.");
            var sku=resolve(state,items.get(0));var sellers=sku.getAsJsonArray("sellers");
            if(sellers==null||sellers.size()!=1)throw new IllegalArgumentException("Cencosud publica vendedores ambiguos.");
            var seller=resolve(state,sellers.get(0));var offer=resolve(state,seller.get("commertialOffer"));
            if(!offer.has("AvailableQuantity")||offer.get("AvailableQuantity").getAsBigDecimal().signum()<=0)throw new IllegalArgumentException("Producto Cencosud sin stock.");
            String price=text(offer,"Price");
            return Optional.of(new Extractor.Quote(Store.required(text(p,"productName"),"Título Cencosud"),PriceParser.parse(price),url,"Cencosud VTEX IO HTML; SKU="+text(sku,"itemId")+"; vendedor="+text(seller,"sellerId")+"; moneda=PEN; precio online=commertialOffer.Price; excluye promociones de tarjeta; verificar ubicación"));
        }return Optional.empty();
    }
    private static JsonObject resolve(JsonObject state,JsonElement ref){
        if(ref==null||!ref.isJsonObject())throw new IllegalArgumentException("Estado Cencosud incompleto.");var o=ref.getAsJsonObject();
        if(o.has("id")&&text(o,"type").equals("id")){var target=state.get(text(o,"id"));if(target==null||!target.isJsonObject())throw new IllegalArgumentException("Referencia Cencosud ausente.");return target.getAsJsonObject();}return o;
    }
    private static String text(JsonObject o,String key){var v=o.get(key);return v!=null&&v.isJsonPrimitive()?v.getAsString():"";}
}
