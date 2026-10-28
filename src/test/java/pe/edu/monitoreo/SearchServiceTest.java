package pe.edu.monitoreo;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import java.net.URI;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SearchServiceTest {
    static Store.Product product(){return new Store.Product(1,"PV-1","Leche GLORIA Entera Caja 1 L","GLORIA","Entera","Caja","1 L","Lácteos","leche,gloria",new BigDecimal("4"),new BigDecimal("7"),true);}
    static String productHtml(String title,String price){return "<h1>"+title+"</h1><script type='application/ld+json'>{\"@type\":\"Product\",\"name\":\""+title+"\",\"offers\":{\"priceCurrency\":\"PEN\",\"price\":\""+price+"\"}}</script>";}
    static String resultLinks(){return "<a href='/leche-gloria-entera-1l/p'>Leche GLORIA Entera Caja 1L</a><a href='/leche-gloria-entera-1l/p?utm_source=duplicado'>Leche GLORIA Entera Caja 1L</a><a href='/leche-gloria-500ml/p'>Leche GLORIA Caja 500ml</a><a href='https://otra.example/leche-gloria/p'>Leche GLORIA Entera</a><a href='/cart'>Leche GLORIA Entera</a>";}
    @Test void discoversGetSearchAndExtractsMultipleProducts()throws Exception {
        var requested=new ArrayList<String>();var search=new SearchService(url->{requested.add(url);String path=URI.create(url).getPath();String html=path.equals("/")?"<form action='/buscar' method='get'><input type='search' name='q'></form>":path.equals("/buscar")?resultLinks():productHtml(path.contains("500")?"Leche GLORIA Caja 500ml":"Leche GLORIA Entera Caja 1L",path.contains("500")?"3.20":"5.30");return Jsoup.parse(html,url);},14,8);
        var result=search.search(product(),"https://tienda.example/");assertEquals(2,result.quotes().size());assertFalse(result.searchUrls().isEmpty());assertTrue(requested.stream().anyMatch(u->u.contains("/buscar?q=")));assertTrue(requested.stream().allMatch(u->URI.create(u).getHost().equals("tienda.example")));assertFalse(requested.stream().anyMatch(u->u.contains("/cart")));assertEquals(2,requested.stream().filter(u->URI.create(u).getPath().endsWith("/p")).count());assertTrue(result.quotes().getFirst().title().contains("1L"));assertTrue(result.quotes().getFirst().evidence().contains("homologación pendiente"));
    }
    @Test void detectsVtexSearchWithoutNeedingAProductUrl()throws Exception {
        var search=new SearchService(url->{String path=URI.create(url).getPath();return Jsoup.parse(path.equals("/")?"<script src='https://cdn.vtex.com/store.js'></script>":path.equals("/busca")?resultLinks():productHtml("Leche GLORIA Entera Caja 1L","5.50"),url);},14,8);var result=search.search(product(),"https://tienda.example/");assertTrue(result.searchUrls().stream().allMatch(u->u.contains("/busca?ft=")));assertFalse(result.quotes().isEmpty());
    }
    @Test void detectsDeclaredSearchAction()throws Exception {
        var search=new SearchService(url->{String path=URI.create(url).getPath();String html=path.equals("/")?"<script type='application/ld+json'>{\"@type\":\"WebSite\",\"potentialAction\":{\"@type\":\"SearchAction\",\"target\":{\"urlTemplate\":\"https://tienda.example/catalogo?q={search_term_string}\"}}}</script>":path.equals("/catalogo")?resultLinks():productHtml("Leche GLORIA Entera Caja 1L","5.30");return Jsoup.parse(html,url);},14,8);assertTrue(search.search(product(),"https://tienda.example/").searchUrls().stream().anyMatch(u->u.contains("/catalogo?q=")));
    }
    @Test void emptyResultsAreExplicit()throws Exception {var result=new SearchService(url->Jsoup.parse("<h1>Sin productos</h1>",url),8,3).search(product(),"https://tienda.example/");assertTrue(result.quotes().isEmpty());assertFalse(result.incidents().isEmpty());}
    @Test void respectsPageAndProductBudgets()throws Exception {
        var requested=new ArrayList<String>();var search=new SearchService(url->{requested.add(url);String html;if(URI.create(url).getPath().equals("/")){var text=new StringBuilder();for(int i=0;i<30;i++)text.append("<a href='/leche-gloria-").append(i).append("/p'>Leche GLORIA Entera Caja 1L</a>");html=text.toString();}else html=productHtml("Leche GLORIA Entera Caja 1L","5.30");return Jsoup.parse(html,url);},4,2);var result=search.search(product(),"https://tienda.example/");assertTrue(requested.size()<=4);assertEquals(2,result.quotes().size());
    }
    @Test void preservesEncodedSearchTermsAndRemovesTracking(){assertEquals("https://tienda.example/buscar?q=leche%26gloria",SearchService.canonical("https://tienda.example/buscar?q=leche%26gloria&utm_source=x#top"));}
    @Test void doesNotFollowAnExternalSearchForm()throws Exception {var called=new ArrayList<String>();var result=new SearchService(url->{called.add(url);return Jsoup.parse("<form action='https://otra.example/buscar'><input type=search name=q></form>",url);},5,2).search(product(),"https://tienda.example/");assertTrue(result.quotes().isEmpty());assertEquals(1,called.size());}
    @Test void aRestrictionStopsSearch() {var calls=new ArrayList<String>();assertThrows(org.jsoup.HttpStatusException.class,()->new SearchService(url->{calls.add(url);throw new org.jsoup.HttpStatusException("Acceso restringido",403,url);},5,2).search(product(),"https://tienda.example/"));assertEquals(1,calls.size());}
    @Test void prioritizesSpecificVarietyWhenOtherAttributesTie()throws Exception {
        var own=new Store.Product(1,"PV-1","Leche Reconstituida Entera GLORIA Lata 390g Paquete 6un","GLORIA","","Lata","390 g · 6 un","Lácteos","gloria",BigDecimal.ONE,BigDecimal.TEN,true);
        var search=new SearchService(url->{String path=URI.create(url).getPath();String html=path.equals("/")?"<a href='/leche-gloria-evaporada-390g-sixpack/p'>Leche Gloria Evaporada Entera Sixpack Lata 390 g</a><a href='/leche-gloria-reconstituida-390g-sixpack/p'>Leche Gloria Reconstituida Entera Sixpack Lata 390 g</a>":productHtml(path.contains("evaporada")?"Leche Gloria Evaporada Entera Sixpack Lata 390 g":"Leche Gloria Reconstituida Entera Sixpack Lata 390 g","21.50");return Jsoup.parse(html,url);},10,8);
        var result=search.search(own,"https://tienda.example/");assertEquals(2,result.quotes().size());assertTrue(result.quotes().getFirst().title().contains("Reconstituida"));
    }
}
