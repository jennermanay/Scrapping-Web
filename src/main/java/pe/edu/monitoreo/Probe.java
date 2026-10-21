package pe.edu.monitoreo;

/** Read-only CLI to verify a real page independently of PostgreSQL/JavaFX. */
public final class Probe {
    public static void main(String[] args)throws Exception {
        String url=args.length>0?args[0]:Samples.PLAZA_URL;
        String strategy=args.length>1?args[1]:"VTEX";
        var source=new Store.Source(0,"Prueba de fuente",url,args.length>2?args[2]:"h1",args.length>3?args[3]:".price","","",strategy,60,24,true);
        var quote=new Extractor().extract(source,url);
        System.out.println(quote.title()+"\nPEN "+quote.price()+"\n"+quote.url()+"\n"+quote.evidence());
    }
}
