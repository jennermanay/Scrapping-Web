package pe.edu.monitoreo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Scraper {
    public record RunResult(long executionId,int candidates,String summary){}
    private static final Logger LOG=LoggerFactory.getLogger(Scraper.class);
    private final Store store;
    private final IExtractorFuente extractor;
    private final ISearchFuente search;
    public Scraper(Store store){this(store,new Extractor(),new SearchService());}
    public Scraper(Store store,IExtractorFuente extractor){this(store,extractor,new SearchService());}
    public Scraper(Store store,IExtractorFuente extractor,ISearchFuente search){this.store=store;this.extractor=extractor;this.search=search;}
    public RunResult run(Store.Link selected)throws Exception {return run(selected,null);}
    public RunResult run(Store.Link selected,Store.Homologated approved)throws Exception {
        // Reload config to avoid executing stale URLs or disabled links selected before an edit.
        var link=store.links().stream().filter(l->l.id()==selected.id()).findFirst().orElseThrow();
        if(approved!=null){if(approved.productId()!=link.productId()||approved.sourceId()!=link.sourceId())throw new IllegalArgumentException("Homologación no corresponde a la fuente.");link=new Store.Link(link.id(),link.productId(),link.sourceId(),link.product(),link.source(),approved.url(),link.condition(),link.scheduled(),link.active(),link.reference(),false);}
        var source=store.source(link.sourceId());long run=store.start(link);var actor=store.session();
        try {
            String summary="Precio capturado; homologación pendiente.";int count=1;
            if(link.search()){var result=search.search(store.product(link.productId()),link.url());checkSession(actor);store.searchSuccess(run,result,link.condition());summary=result.summary();count=result.quotes().size();}
            else {var quote=extractor.extract(source,link.url());checkSession(actor);if(approved==null)store.success(run,quote,link.condition());else store.completeHomologated(run,quote,link.condition(),approved.id());}
            LOG.info("ejecucion="+run+" fuente="+source.id()+" "+summary);return new RunResult(run,count,summary);
        }
        catch(Exception e){String error=e.getClass().getSimpleName()+": "+e.getMessage();LOG.warn("ejecucion={} fuente={} error={}",run,source.id(),e.getClass().getSimpleName());try{store.fail(run,error);}catch(Exception log){e.addSuppressed(log);}throw e;}
    }
    private void checkSession(Store.User actor)throws InterruptedException {if(Thread.currentThread().isInterrupted()||actor==null||store.session()==null||store.session().id()!=actor.id())throw new InterruptedException("Consulta cancelada.");}
}
