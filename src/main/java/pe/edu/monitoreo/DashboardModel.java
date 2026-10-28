package pe.edu.monitoreo;

import com.google.common.collect.ImmutableList;
import java.util.*;

/** Immutable catalog snapshot shared by the background worker and the JavaFX view. */
public record DashboardModel(List<Store.Product> products,List<Store.Source> sources,List<Store.Link> links,List<Store.AlertItem> alerts,Map<Long,Store.MonitoringSettings> monitoring,List<Store.Role> roles,Set<Module> permissions) {
    public DashboardModel(List<Store.Product> products,List<Store.Source> sources,List<Store.Link> links,List<Store.AlertItem> alerts){this(products,sources,links,alerts,Map.of(),List.of(),EnumSet.allOf(Module.class));}
    public DashboardModel {
        monitoring=Map.copyOf(monitoring);roles=List.copyOf(roles);permissions=Set.copyOf(permissions);
        products=ImmutableList.copyOf(products);sources=ImmutableList.copyOf(sources);
        links=ImmutableList.copyOf(links);alerts=ImmutableList.copyOf(alerts);
    }
    public long pendingAlerts(){return alerts.stream().filter(a->a.status().equals("PENDIENTE")).count();}
    public long activeSources(){return sources.stream().filter(Store.Source::active).count();}
    public String summary(){return "Productos: "+products.size()+" · Fuentes configuradas: "+sources.size()+" · Fuentes activas: "+activeSources()+" · Alertas: "+alerts.size()+" · Pendientes: "+pendingAlerts();}

    /** Active external sources; own-price references and fixture sources are excluded. */
    public List<Store.Source> externalSources(){return sources.stream().filter(s->s.active()&&!s.strategy().equals("DEMO")&&!links.stream().anyMatch(l->l.sourceId()==s.id()&&l.reference())&&!Set.of("plazavea.com.pe","www.plazavea.com.pe").contains(Objects.toString(java.net.URI.create(s.url()).getHost(),""))).toList();}
    private List<Store.Link> monitoredLinks(){var activeProducts=products.stream().filter(Store.Product::active).map(Store.Product::id).collect(java.util.stream.Collectors.toSet());var activeSources=externalSources().stream().map(Store.Source::id).collect(java.util.stream.Collectors.toSet());return links.stream().filter(l->l.active()&&!l.reference()&&activeProducts.contains(l.productId())&&activeSources.contains(l.sourceId())).toList();}
    public long monitoredProducts(){return monitoredLinks().stream().map(Store.Link::productId).distinct().count();}
    public Map<Long,Long> monitoredCounts(){var result=new LinkedHashMap<Long,Long>();var enabled=monitoredLinks();for(var source:externalSources())result.put(source.id(),enabled.stream().filter(l->l.sourceId()==source.id()).map(Store.Link::productId).distinct().count());return Collections.unmodifiableMap(result);}
    public Map<String,Long> alertCounts(){var result=new LinkedHashMap<String,Long>();for(String state:List.of("PENDIENTE","EN_REVISION","ATENDIDA","DESCARTADA"))result.put(state,0L);for(var alert:alerts)result.merge(alert.status(),1L,Long::sum);return Collections.unmodifiableMap(result);}
}
