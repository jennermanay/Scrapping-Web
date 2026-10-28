package pe.edu.monitoreo;

import java.util.*;

/** Executes each enabled source independently and returns only this batch's captures. */
public final class ProductScraping {
    public record SourceResult(String source,String state,int count,String detail,long sourceId) {}
    public record Result(List<SourceResult> sources,List<Store.Candidate> candidates) {}
    private final Store store;
    private final IExtractorFuente extractor;
    private final ISearchFuente search;
    public ProductScraping(Store store,IExtractorFuente extractor,ISearchFuente search){this.store=store;this.extractor=extractor;this.search=search;}
    public Result run(long productId)throws Exception {return run(productId,0);}
    public Result run(long productId,long selectedSource)throws Exception {return run(productId,selectedSource,false);}
    public Result run(long productId,long selectedSource,boolean scheduled)throws Exception {
        store.requireModule(Module.EXTRACCION);
        var enabled=new HashSet<Long>();for(var source:store.sources())if(source.active())enabled.add(source.id());
        var links=store.links().stream().filter(l->l.productId()==productId&&(selectedSource==0||l.sourceId()==selectedSource)&&l.active()&&!l.reference()&&enabled.contains(l.sourceId())).toList();
        if(links.isEmpty())throw new IllegalArgumentException("El producto no tiene fuentes activas.");
        var rows=new ArrayList<SourceResult>();var candidates=new ArrayList<Store.Candidate>();
        for(var link:links){
            var approved=store.homologated(productId,link.sourceId());
            if(scheduled){if(!link.scheduled())continue;approved=approved.stream().filter(h->h.state().equals("ACTIVO")).toList();if(approved.isEmpty())continue;}
            var targets=approved.isEmpty()?java.util.Collections.<Store.Homologated>singletonList(null):approved;
            for(var equivalence:targets){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException("Extracción cancelada.");
            try{var run=new Scraper(store,extractor,search).run(link,equivalence);var found=store.candidatesForRun(productId,run.executionId());candidates.addAll(found);rows.add(new SourceResult(link.source(),found.isEmpty()?"SIN RESULTADOS":"CAPTURADO",found.size(),run.summary(),link.sourceId()));}
            catch(InterruptedException e){throw e;}
            catch(Exception e){rows.add(new SourceResult(link.source(),"ERROR",0,Objects.toString(e.getMessage(),e.getClass().getSimpleName()),link.sourceId()));}
            }
        }
        var grouped=new LinkedHashMap<Long,SourceResult>();for(var row:rows){var old=grouped.get(row.sourceId());grouped.put(row.sourceId(),old==null?row:new SourceResult(row.source(),old.state().equals("ERROR")||row.state().equals("ERROR")?"ERROR":old.count()+row.count()>0?"CAPTURADO":"SIN RESULTADOS",old.count()+row.count(),old.detail()+" · "+row.detail(),row.sourceId()));}
        return new Result(List.copyOf(grouped.values()),List.copyOf(candidates));
    }
}
