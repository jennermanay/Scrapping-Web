package pe.edu.monitoreo;

import com.google.gson.*;
import org.jsoup.nodes.Document;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads Tottus' public SSR state; does not execute scripts or call internal APIs. */
final class TottusSupport {
    record ProductLink(String url,String title){}
    private static boolean supports(String url){try{return URI.create(url).getHost().replaceFirst("(?i)^www\\.","").equalsIgnoreCase("tottus.com.pe");}catch(Exception e){return false;}}
    static String searchUrl(Document doc,String query){
        String base=doc.location().isBlank()?doc.baseUri():doc.location();if(!supports(base))return "";
        for(var link:doc.select("a[href]")){
            String value=link.absUrl("href");try{var uri=URI.create(value);if(supports(value)&&uri.getPath().matches("/tottus-pe/buscar/?"))return uri.getScheme()+"://"+uri.getRawAuthority()+uri.getPath()+"?Ntt="+URLEncoder.encode(query,StandardCharsets.UTF_8);}catch(Exception ignored){}
        }
        if(doc.selectFirst("#testId-SearchBar-Input")!=null){var uri=URI.create(base);return uri.getScheme()+"://"+uri.getRawAuthority()+"/tottus-pe/buscar?Ntt="+URLEncoder.encode(query,StandardCharsets.UTF_8);}
        return "";
    }
    static List<ProductLink> productLinks(Document doc){
        String base=doc.location().isBlank()?doc.baseUri():doc.location();if(!supports(base))return List.of();
        var props=pageProps(doc);if(props==null||!props.has("results")||!props.get("results").isJsonArray())return List.of();
        var out=new ArrayList<ProductLink>();for(var element:props.getAsJsonArray("results")){
            if(!element.isJsonObject())continue;var item=element.getAsJsonObject();String url=value(item,"url"),name=value(item,"displayName"),sku=value(item,"skuId");
            if(url.isBlank()||name.isBlank())continue;url=URI.create(base).resolve(url).toString();
            if(!supports(url)||!URI.create(url).getPath().startsWith("/tottus-pe/articulo/"))continue;
            if(sku.matches("[0-9]+")&&!URI.create(url).getPath().endsWith("/"+sku))url=url.replaceFirst("/$","")+"/"+sku;
            out.add(new ProductLink(url,name));
        }return List.copyOf(out);
    }
    static Optional<Extractor.Quote> quote(Document doc,String url){
        if(!supports(url))return Optional.empty();var props=pageProps(doc);
        if(props==null||!props.has("productData")||!props.get("productData").isJsonObject())return Optional.empty();
        var product=props.getAsJsonObject("productData");if(bool(product,"isOutOfStock"))throw new IllegalArgumentException("Tottus informa producto sin stock.");
        var variants=product.getAsJsonArray("variants");if(variants==null||variants.isEmpty())throw new IllegalArgumentException("Tottus no publica variantes disponibles.");
        String selected=value(product,"currentVariant");if(selected.isBlank())selected=value(product,"primaryVariantId");
        var chosen=new ArrayList<JsonObject>();for(var variant:variants)if(variant.isJsonObject()&&value(variant.getAsJsonObject(),"id").equals(selected))chosen.add(variant.getAsJsonObject());
        if(chosen.size()!=1)throw new IllegalArgumentException("No se puede identificar una variante única de Tottus.");
        var variant=chosen.getFirst();String last=URI.create(url).getPath().replaceFirst("/$","").replaceFirst(".*/","");
        if(last.matches("[0-9]+")&&!last.equals(selected))throw new IllegalArgumentException("La variante de Tottus no coincide con la URL consultada.");
        if(!bool(variant,"isPurchaseable")||(variant.has("isOnlineSellable")&&!bool(variant,"isOnlineSellable")))throw new IllegalArgumentException("Tottus informa una variante no disponible para venta online.");
        var offerings=variant.getAsJsonArray("offerings");var active=new ArrayList<JsonObject>();if(offerings!=null)for(var offer:offerings)if(offer.isJsonObject()&&bool(offer.getAsJsonObject(),"isActive"))active.add(offer.getAsJsonObject());
        if(active.size()!=1)throw new IllegalArgumentException("Tottus no publica un vendedor activo inequívoco.");
        var prices=variant.getAsJsonArray("prices");if(prices==null)throw new IllegalArgumentException("Tottus no publica precios de la variante.");
        var internet=new ArrayList<JsonObject>();var normal=new ArrayList<JsonObject>();
        for(var raw:prices){if(!raw.isJsonObject())continue;var price=raw.getAsJsonObject();if(bool(price,"crossed"))continue;
            String label=value(price,"label").toLowerCase(Locale.ROOT);if(label.contains("cmr")||label.contains("tarjeta")||label.contains("membres"))continue;
            if(value(price,"type").equals("internetPrice"))internet.add(price);else if(value(price,"type").equals("normalPrice"))normal.add(price);
        }
        var candidates=internet.isEmpty()?normal:internet;if(candidates.size()!=1)throw new IllegalArgumentException("Tottus no publica un precio web no condicionado inequívoco.");
        var price=candidates.getFirst();if(!Set.of("S/","S/.","PEN").contains(value(price,"symbol").trim()))throw new IllegalArgumentException("No se pudo verificar moneda PEN en Tottus.");
        var amounts=price.getAsJsonArray("price");if(amounts==null||amounts.size()!=1||!amounts.get(0).isJsonPrimitive())throw new IllegalArgumentException("Tottus publica un rango o precio ambiguo.");
        var amount=PriceParser.parse(amounts.get(0).getAsString());String title=Store.required(value(product,"name"),"Nombre del producto Tottus");
        return Optional.of(new Extractor.Quote(title,amount,url,"Tottus HTML __NEXT_DATA__; SKU="+selected+"; vendedor="+value(active.getFirst(),"sellerName")+"; tipo="+value(price,"type")+"; precio="+amount+"; moneda=PEN; no se usan precios tachados ni de tarjeta; revisar formato y condiciones"));
    }
    private static JsonObject pageProps(Document doc){
        var script=doc.selectFirst("script#__NEXT_DATA__[type=application/json]");if(script==null)return null;
        var root=JsonParser.parseString(script.data()).getAsJsonObject();if(!root.has("props"))return null;var props=root.getAsJsonObject("props");return props.has("pageProps")&&props.get("pageProps").isJsonObject()?props.getAsJsonObject("pageProps"):null;
    }
    private static String value(JsonObject object,String key){var value=object.get(key);return value==null||!value.isJsonPrimitive()?"":value.getAsString();}
    private static boolean bool(JsonObject object,String key){return object.has(key)&&object.get(key).isJsonPrimitive()&&object.get(key).getAsBoolean();}
}
