package pe.edu.monitoreo;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import com.google.gson.JsonParser;

/** Loads the retailer's own product before configuring comparison sources. */
public final class PlazaVeaImporter {
    public record Preview(String code,String title,String brand,String model,String presentation,String capacity,
                          String category,String sku,String ean,BigDecimal price,String url,OffsetDateTime captured,
                          String evidence,List<String> warnings) {}
    public static void validateUrl(String url){
        Extractor.validateUrl(url,false);var uri=URI.create(url);
        if(!uri.getScheme().equalsIgnoreCase("https")||!Set.of("www.plazavea.com.pe","plazavea.com.pe").contains(uri.getHost().toLowerCase(Locale.ROOT))||!uri.getPath().matches(".*/p/?$"))
            throw new IllegalArgumentException("Ingresa la URL HTTPS de un producto de Plaza Vea, terminada en /p.");
    }
    public Preview inspect(String url)throws Exception {
        validateUrl(url);var document=new Extractor().fetch(url);validateUrl(document.location());return parse(document,document.location());
    }
    Preview parse(Document doc,String url){
        validateUrl(url);var source=new Store.Source(0,"Plaza Vea",url,"","","","","VTEX",60,24,true);
        var quote=new Extractor().parse(doc,source,url);var data=Extractor.vtexProduct(doc);var sku=data.getAsJsonArray("skus").get(0).getAsJsonObject();
        String brand=metadata(doc,"productBrandName"),category=metadata(doc,"productDepartmentName");
        String presentation=doc.select("td.value-field.Presentacion-Empaque").text();
        String model=doc.select("td.value-field.Denominacion-Variedad").text();if(model.isBlank())model=doc.select("td.value-field.Denominacion-Contenido-De-Grasa").text();
        String specCapacity=doc.select("td.value-field.Contenido-Neto").text();
        var units=Pattern.compile("(?i)(\\d+(?:[.,]\\d+)?)\\s*(ml|kg|g|l|un(?:d)?|litros?)\\b").matcher(quote.title());var sizes=new LinkedHashSet<String>();while(units.find())sizes.add(units.group(1)+" "+units.group(2).toLowerCase(Locale.ROOT));
        String capacity=sizes.isEmpty()?specCapacity:String.join(" · ",sizes);var warnings=new ArrayList<String>();
        if(!specCapacity.isBlank()&&!sizes.isEmpty()&&!specCapacity.replaceAll("\\s","").equalsIgnoreCase(capacity.replaceAll("\\s","")))warnings.add("El título indica "+capacity+" y Contenido Neto indica "+specCapacity+". Verifica la presentación antes de guardar.");
        if(brand.isBlank())warnings.add("La marca no se pudo extraer; complétala tras revisar la ficha.");
        if(presentation.isBlank())warnings.add("Completa la presentación que muestra la ficha.");
        if(capacity.isBlank())warnings.add("Completa la capacidad y su unidad.");
        String id=metadata(doc,"productReferenceId");if(id.isBlank())id=data.get("productId").getAsString();
        String ean=sku.has("ean")?sku.get("ean").getAsString():"";
        return new Preview("PV-"+id,quote.title(),brand,model,presentation,capacity,category.isBlank()?"General":category,sku.get("sku").getAsString(),ean,quote.price(),quote.url(),OffsetDateTime.now(),quote.evidence(),List.copyOf(warnings));
    }
    private static String metadata(Document doc,String key){
        var pattern=Pattern.compile("\""+Pattern.quote(key)+"\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\")");var values=new LinkedHashSet<String>();
        for(var script:doc.select("script")){var m=pattern.matcher(script.data());while(m.find())values.add(JsonParser.parseString(m.group(1)).getAsString());}
        return values.size()==1?values.iterator().next():"";
    }
}
