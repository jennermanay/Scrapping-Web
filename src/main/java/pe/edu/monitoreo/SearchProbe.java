package pe.edu.monitoreo;

import java.nio.file.*;
import java.util.*;

/** Searches a real store without touching application accounts or configured products. */
public final class SearchProbe {
    public static void main(String[] args)throws Exception {
        String site=args.length>0?args[0]:"https://www.makro.plazavea.com.pe/";
        var own=new PlazaVeaImporter().inspect(args.length>1?args[1]:Samples.PLAZA_URL);
        var product=new Store.Product(0,own.code(),own.title(),own.brand(),own.model(),own.presentation(),own.capacity(),own.category(),"gloria,leche",null,own.price(),true);
        var result=new SearchService().search(product,site);System.out.println(result.summary());for(var quote:result.quotes())System.out.println(quote.title()+" | PEN "+quote.price()+" | "+quote.url());
        for(var incident:result.incidents())System.out.println("Incidencia: "+incident);
        var rows=result.quotes().stream().map(q->List.of(q.title(),q.price().toPlainString(),q.url(),q.evidence())).toList();Files.createDirectories(Path.of("artifacts"));Reports.csv(new ReportData("Búsqueda automática desde URL de tienda",List.of("producto","precio_PEN","url","evidencia"),rows),Path.of("artifacts","busqueda-real.csv"));
        if(result.quotes().isEmpty())throw new IllegalStateException("La búsqueda no encontró candidatos con precio verificable.");
    }
}
