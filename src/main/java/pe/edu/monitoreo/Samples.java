package pe.edu.monitoreo;

import java.math.BigDecimal;

public final class Samples {
    private Samples() {}
    public static void demo(Store store)throws Exception {load(store,"DEMO-1","Producto demostración","Demo","General","1 L","Demostración local","demo://producto","DEMO",new BigDecimal("80"),new BigDecimal("120"));}
    public static final String PLAZA_URL="https://www.plazavea.com.pe/leche-gloria-entera-uhtcon-vitamina-a-cy-d-caja-1l/p";
    public static final String MAKRO_URL="https://www.makro.plazavea.com.pe/leche-gloria-entera-uhtcon-vitamina-a-cy-d-caja-1l/p";
    public static void plazaVea(Store store)throws Exception {load(store,"GLORIA-946","Leche Entera UHT GLORIA Caja 946ml","GLORIA","Lácteos","946 ml","Plaza Vea · HTML VTEX",PLAZA_URL,"VTEX",new BigDecimal("4.00"),new BigDecimal("7.00"));}
    public static void realPages(Store store)throws Exception {plazaVea(store);load(store,"GLORIA-946","Leche Entera UHT GLORIA Caja 946ml","GLORIA","Lácteos","946 ml","Makro · HTML VTEX",MAKRO_URL,"VTEX",new BigDecimal("4.00"),new BigDecimal("7.00"));}
    private static void load(Store store,String code,String name,String brand,String category,String capacity,String source,String url,String strategy,BigDecimal cost,BigDecimal price)throws Exception {
        if(store.products().stream().noneMatch(p->p.code().equals(code)))store.saveProduct(new Store.Product(0,code,name,brand,"Entera UHT",strategy.equals("DEMO")?"Botella":"Caja",capacity,category,"leche,entera",cost,price,true));
        if(store.sources().stream().noneMatch(s->s.name().equals(source)))store.saveSource(new Store.Source(0,source,url,"h1",".precio","","",strategy,60,24,true));
        var p=store.products().stream().filter(x->x.code().equals(code)).findFirst().orElseThrow();var s=store.sources().stream().filter(x->x.name().equals(source)).findFirst().orElseThrow();
        if(store.links().stream().noneMatch(l->l.productId()==p.id()&&l.sourceId()==s.id()))store.saveLink(0,p.id(),s.id(),url,strategy.equals("DEMO")?"Ficticio de prueba":"Precio web público; verificar ubicación, canal y condiciones. Costo y precio propio son ejemplos de prueba.",false,true);
    }
}
