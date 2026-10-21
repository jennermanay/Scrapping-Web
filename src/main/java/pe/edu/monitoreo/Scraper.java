package pe.edu.monitoreo;

import java.nio.file.*;
import java.util.logging.*;

public final class Scraper {
    private static final Logger LOG=Logger.getLogger(Scraper.class.getName());
    static {try {Files.createDirectories(Path.of("logs"));var handler=new FileHandler("logs/scraping-%g.log",1_000_000,3,true);handler.setFormatter(new SimpleFormatter());LOG.addHandler(handler);}catch(Exception e){LOG.warning("No se pudo configurar el archivo de logs.");}}
    private final Store store;
    private final IExtractorFuente extractor;
    public Scraper(Store store){this(store,new Extractor());}
    public Scraper(Store store,IExtractorFuente extractor){this.store=store;this.extractor=extractor;}
    public void run(Store.Link selected)throws Exception {
        // Reload config to avoid executing stale URLs or disabled links selected before an edit.
        var link=store.links().stream().filter(l->l.id()==selected.id()).findFirst().orElseThrow();
        var source=store.source(link.sourceId());long run=store.start(link);
        try {store.success(run,extractor.extract(source,link.url()),link.condition());LOG.info("ejecucion="+run+" fuente="+source.id()+" estado=OK");}
        catch(Exception e){String error=e.getClass().getSimpleName()+": "+e.getMessage();LOG.warning("ejecucion="+run+" fuente="+source.id()+" "+error);try{store.fail(run,error);}catch(Exception log){e.addSuppressed(log);}throw e;}
    }
}
