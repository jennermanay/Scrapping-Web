package pe.edu.monitoreo;

import org.jsoup.nodes.*;
import com.google.gson.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Bounded same-site search and HTML crawling. Discovery never confirms equivalence. */
public final class SearchService implements ISearchFuente {
    @FunctionalInterface interface PageLoader {Document fetch(String url)throws Exception;}
    public record Result(String query,List<Extractor.Quote> quotes,List<String> searchUrls,int pagesRead,List<String> incidents) {
        public String summary(){return "Búsqueda: "+query+" · páginas consultadas: "+pagesRead+" · candidatos: "+quotes.size()+" · incidencias: "+incidents.size();}
    }
    private record Page(String url,int depth){}
    private record Ranked(String url,int score){}
    private final PageLoader loader;
    private final int pageLimit,productLimit;
    private final boolean throttle;
    public SearchService(){this(new Extractor()::fetch,14,8,true);}
    SearchService(PageLoader loader,int pages,int products){this(loader,pages,products,false);}
    private SearchService(PageLoader loader,int pages,int products,boolean throttle){this.loader=loader;pageLimit=pages;productLimit=products;this.throttle=throttle;}
    @Override public Result search(Store.Product product,String siteUrl)throws Exception {
        Extractor.validateUrl(siteUrl,false);String site=canonical(siteUrl);var origin=URI.create(site);
        String query=query(product);var tokens=tokens(product.name()+" "+product.brand()+" "+product.model());
        var visited=new LinkedHashSet<String>();var searchUrls=new LinkedHashSet<String>();var incidents=new ArrayList<String>();var quotes=new LinkedHashMap<String,Extractor.Quote>();
        var listings=new ArrayDeque<Page>();listings.add(new Page(site,0));var productPages=new HashMap<String,Integer>();
        while(!listings.isEmpty()&&visited.size()<Math.min(5,pageLimit)) {
            var page=listings.removeFirst();if(!sameSite(origin,page.url())||!visited.add(page.url()))continue;
            Document doc;
            try{doc=read(page.url(),visited.size());}catch(org.jsoup.HttpStatusException e){if(page.depth()==0||e.getStatusCode()==403||e.getStatusCode()==429)throw e;incidents.add("HTTP "+e.getStatusCode()+" en búsqueda "+page.url());continue;}
            catch(java.io.IOException e){if(page.depth()==0)throw e;incidents.add("Búsqueda sin respuesta: "+page.url()+" · "+e.getMessage());continue;}
            if(!sameSite(origin,doc.location()))throw new IllegalArgumentException("La web redirige a otro dominio. Indica su URL final para buscar allí.");
            // A user may also provide a product URL. Accept it only when its own title is relevant.
            try{var quote=new Extractor().auto(doc,canonical(doc.location()));if(relevance(quote.title(),tokens)>0)quotes.put(quote.url(),evidence(quote,query,doc.location()));}catch(IllegalArgumentException ignored){}
            if(page.depth()==0){
                var variants=new LinkedHashSet<String>();variants.add(query);variants.add(reducedQuery(product));
                for(String variant:variants)for(String search:searchTargets(doc,variant))if(searchUrls.size()<2&&sameSite(origin,search)&&searchUrls.add(search))listings.addLast(new Page(search,1));
            }
            for(var ranked:links(doc,tokens,origin))if(!visited.contains(ranked.url()))productPages.merge(ranked.url(),ranked.score(),Math::max);
            // Follow explicit pagination only, never arbitrary navigation or account/cart actions.
            if(page.depth()<2)for(var a:doc.select("a[rel=next],a.next,a[aria-label*=iguiente]")){String next=canonicalOrEmpty(a.absUrl("href"));if(!next.isEmpty()&&sameSite(origin,next)&&listings.size()<2)listings.add(new Page(next,page.depth()+1));}
            if(page.depth()==0&&searchUrls.isEmpty()&&productPages.isEmpty())incidents.add("No se detectó un buscador HTML ni enlaces de productos relacionados. La página puede depender de JavaScript.");
        }
        var ordered=productPages.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed().thenComparing(Map.Entry::getKey)).toList();int examined=0;
        for(var entry:ordered){
            if(visited.size()>=pageLimit||examined>=productLimit||quotes.size()>=productLimit)break;
            String url=entry.getKey();if(!visited.add(url))continue;examined++;
            try{
                Document doc=read(url,visited.size());if(!sameSite(origin,doc.location())){incidents.add("Se omitió una redirección externa: "+url);continue;}
                var quote=new Extractor().auto(doc,canonical(doc.location()));
                if(relevance(quote.title(),tokens)>0)quotes.putIfAbsent(quote.url(),evidence(quote,query,String.join(" ; ",searchUrls)));
            }catch(org.jsoup.HttpStatusException e){
                incidents.add("HTTP "+e.getStatusCode()+" en "+url);if(e.getStatusCode()==403||e.getStatusCode()==429)break;
            }catch(Exception e){incidents.add(url+": "+e.getMessage());}
        }
        if(quotes.isEmpty())incidents.add("No se encontraron productos con precio PEN verificable en las páginas consultadas. No se ha generado ninguna equivalencia ni comparación.");
        var sorted=quotes.values().stream().sorted(Comparator.comparingInt((Extractor.Quote q)->Equivalence.assess(product,q.title()).confidence()).reversed().thenComparing(Comparator.comparingInt((Extractor.Quote q)->relevance(q.title(),tokens)).reversed()).thenComparing(Extractor.Quote::title)).toList();
        return new Result(query,List.copyOf(sorted),List.copyOf(searchUrls),visited.size(),List.copyOf(incidents));
    }
    private Document read(String url,int count)throws Exception {if(Thread.currentThread().isInterrupted())throw new InterruptedException("Búsqueda cancelada.");if(throttle&&count>1)Thread.sleep(350);return loader.fetch(url);}
    static String query(Store.Product product){return product.name().strip().replaceAll("\\s+"," ").substring(0,Math.min(140,product.name().strip().replaceAll("\\s+"," ").length()));}
    private static String reducedQuery(Store.Product product){var brand=tokens(product.brand());var parts=new ArrayList<String>();parts.add(product.brand().strip());tokens(product.name()).stream().filter(t->!brand.contains(t)).limit(2).forEach(parts::add);return String.join(" ",parts).strip();}
    private static Extractor.Quote evidence(Extractor.Quote q,String query,String search){return new Extractor.Quote(q.title(),q.price(),q.url(),q.evidence()+"; búsqueda automática="+query+"; páginas de búsqueda="+search+"; homologación pendiente");}
    private static List<String> searchTargets(Document doc,String query){
        var targets=new LinkedHashSet<String>();String encoded=URLEncoder.encode(query,StandardCharsets.UTF_8);
        String wong=WongSupport.searchUrl(doc,query);if(!wong.isBlank())targets.add(wong);
        String tottus=TottusSupport.searchUrl(doc,query);if(!tottus.isBlank())targets.add(tottus);
        for(var form:doc.select("form")){
            if(!form.attr("method").isBlank()&&!form.attr("method").equalsIgnoreCase("get"))continue;
            var input=form.select("input[name]").stream().filter(e->Set.of("q","s","ft","search","query","keyword","keywords","search_query","k").contains(e.attr("name").toLowerCase(Locale.ROOT))||e.attr("type").equalsIgnoreCase("search")).findFirst();
            if(input.isEmpty())continue;String action=form.hasAttr("action")&&!form.attr("action").isBlank()?form.absUrl("action"):doc.location();if(action.isBlank())action=doc.baseUri();
            String params=URLEncoder.encode(input.get().attr("name"),StandardCharsets.UTF_8)+"="+encoded;
            for(var hidden:form.select("input[type=hidden][name]"))if(Set.of("post_type","type","category").contains(hidden.attr("name")))params+="&"+URLEncoder.encode(hidden.attr("name"),StandardCharsets.UTF_8)+"="+URLEncoder.encode(hidden.attr("value"),StandardCharsets.UTF_8);
            targets.add(canonicalOrEmpty(action+(action.contains("?")?"&":"?")+params));
        }
        // SearchAction is a public declaration of the site's own search URL.
        for(var script:doc.select("script[type=application/ld+json]"))try{searchActions(JsonParser.parseString(script.data()),query,targets,doc.location());}catch(JsonParseException ignored){}
        // VTEX storefronts expose full-text search as HTML at /busca?ft=.
        if(targets.isEmpty()&&doc.outerHtml().toLowerCase(Locale.ROOT).contains("vtex")){var uri=URI.create(doc.location().isBlank()?doc.baseUri():doc.location());targets.add(uri.getScheme()+"://"+uri.getRawAuthority()+"/busca?ft="+encoded);}
        targets.remove("");return targets.stream().limit(2).toList();
    }
    private static void searchActions(JsonElement e,String query,Set<String> out,String base){
        if(e.isJsonArray()){for(var item:e.getAsJsonArray())searchActions(item,query,out,base);return;}if(!e.isJsonObject())return;var o=e.getAsJsonObject();
        if(o.has("@type")&&o.get("@type").isJsonPrimitive()&&o.get("@type").getAsString().equals("SearchAction")&&o.has("target")){
            var t=o.get("target");String pattern=t.isJsonPrimitive()?t.getAsString():t.isJsonObject()&&t.getAsJsonObject().has("urlTemplate")?t.getAsJsonObject().get("urlTemplate").getAsString():"";
            if(pattern.contains("{")){String value=pattern.replaceAll("\\{[^}]+\\}",java.util.regex.Matcher.quoteReplacement(URLEncoder.encode(query,StandardCharsets.UTF_8)));out.add(canonicalOrEmpty(URI.create(base).resolve(value).toString()));}
        }
        for(var child:o.entrySet())if(child.getValue().isJsonArray()||child.getValue().isJsonObject())searchActions(child.getValue(),query,out,base);
    }
    private static List<Ranked> links(Document doc,Set<String> tokens,URI origin){
        var links=new ArrayList<Ranked>();
        for(var product:WongSupport.productLinks(doc)){String url=canonicalOrEmpty(product.url());int score=relevance(product.title(),tokens);if(!url.isBlank()&&sameSite(origin,url)&&score>0)links.add(new Ranked(url,score));}
        for(var product:TottusSupport.productLinks(doc)){String url=canonicalOrEmpty(product.url());int score=relevance(product.title(),tokens);if(!url.isBlank()&&sameSite(origin,url)&&score>0)links.add(new Ranked(url,score));}
        for(var a:doc.select("a[href]")){
            String url=canonicalOrEmpty(a.absUrl("href"));if(url.isBlank()||!sameSite(origin,url)||excluded(url))continue;
            String text=a.text()+" "+a.attr("title")+" "+a.select("img[alt]").eachAttr("alt").stream().collect(java.util.stream.Collectors.joining(" "))+" "+URI.create(url).getPath().replace('-',' ');
            int score=relevance(text,tokens);if(score>0)links.add(new Ranked(url,score));
        }return links;
    }
    private static boolean excluded(String url){String path=URI.create(url).getPath().toLowerCase(Locale.ROOT);return path.matches(".*\\.(jpg|jpeg|png|gif|svg|pdf|css|js|zip)$")||Pattern.compile("/(cart|checkout|login|account|mi-cuenta|carrito|wishlist|logout)(/|$)").matcher(path).find();}
    private static Set<String> tokens(String value){var out=new LinkedHashSet<String>();for(String s:normalize(value).split("\\s+"))if(s.length()>=3&&!Set.of("para","con","del","los","las","una","por","sin").contains(s))out.add(s);return out;}
    private static int relevance(String title,Set<String> wanted){String text=" "+normalize(title)+" ";int matched=0;for(var token:wanted)if(text.contains(" "+token+" "))matched++;return matched>=Math.min(2,wanted.size())&&matched>0?matched:0;}
    private static String normalize(String value){return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("\\bsixpack\\b","paquete 6un").replaceAll("\\btripack\\b","paquete 3un").replaceAll("(\\d+(?:[.,]\\d+)?)\\s+(ml|kg|g|l|un)\\b","$1$2").replaceAll("[^a-z0-9]+"," ").trim();}
    static String canonical(String value){Extractor.validateUrl(value,false);var uri=URI.create(value);String host=uri.getHost().toLowerCase(Locale.ROOT);if(host.contains(":"))host="["+host.replace("[","").replace("]","")+"]";
        String query=uri.getRawQuery()==null?"":Arrays.stream(uri.getRawQuery().split("&")).filter(p->!p.toLowerCase(Locale.ROOT).matches("(?:utm_[^=]*|gclid|fbclid|srsltid)=.*")).collect(java.util.stream.Collectors.joining("&"));
        return URI.create(uri.getScheme().toLowerCase(Locale.ROOT)+"://"+host+(uri.getPort()<0?"":":"+uri.getPort())+(uri.getRawPath().isBlank()?"/":uri.getRawPath())+(query.isBlank()?"":"?"+query)).normalize().toASCIIString();}
    private static String canonicalOrEmpty(String url){try{return canonical(url);}catch(Exception e){return "";}}
    private static boolean sameSite(URI origin,String value){try{var other=URI.create(value);return origin.getHost().replaceFirst("(?i)^www\\.","").equalsIgnoreCase(other.getHost().replaceFirst("(?i)^www\\.",""))&&origin.getPort()==other.getPort()&&Set.of("https","http").contains(other.getScheme());}catch(Exception e){return false;}}
}
