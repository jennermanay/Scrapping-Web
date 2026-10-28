package pe.edu.monitoreo;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DashboardModelTest {
 @Test void snapshotCannotBeChangedByWorkerOrView(){var products=new ArrayList<Store.Product>();var sources=new ArrayList<Store.Source>();var alerts=new ArrayList<Store.AlertItem>();sources.add(new Store.Source(1,"Metro","https://www.metro.pe/","","","","","AUTO",60,24,true));alerts.add(new Store.AlertItem(1,"Agua","PRECIO_INFERIOR","PENDIENTE",""));var snapshot=new DashboardModel(products,sources,List.of(),alerts);sources.clear();alerts.clear();assertEquals(1,snapshot.activeSources());assertEquals(1,snapshot.pendingAlerts());assertTrue(snapshot.summary().contains("Alertas: 1"));assertThrows(UnsupportedOperationException.class,()->snapshot.sources().clear());}
 @Test void controllerRejectsMissingDependencies(){assertThrows(NullPointerException.class,()->new MonitoringController(null,new Extractor(),new SearchService()));}
 @Test void logbackIsConfiguredWithRollingFileAppender(){var factory=org.slf4j.LoggerFactory.getILoggerFactory();assertInstanceOf(ch.qos.logback.classic.LoggerContext.class,factory);var context=(ch.qos.logback.classic.LoggerContext)factory;var appender=context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getAppender("FILE");assertInstanceOf(ch.qos.logback.core.rolling.RollingFileAppender.class,appender);assertTrue(appender.isStarted());}

 @Test void chartCountsExcludeDisabledAndOwnSourcesAndCountEachProductOnce(){
  var money=java.math.BigDecimal.TEN;var active=new Store.Product(1,"P1","Agua","Cielo","","Bidón","7 L","Aguas","",money,money,true);var inactive=new Store.Product(2,"P2","Agua","Cielo","","Bidón","7 L","Aguas","",money,money,false);
  var external=new Store.Source(1,"Metro","https://www.metro.pe/","","","","","AUTO",60,24,true);var disabled=new Store.Source(2,"Wong","https://www.wong.pe/","","","","","AUTO",60,24,false);var own=new Store.Source(3,"Plaza Vea","https://www.plazavea.com.pe/","","","","","VTEX",60,24,true);
  var links=List.of(new Store.Link(1,1,1,"Agua","Metro","https://www.metro.pe/","",false,true,false,true),new Store.Link(2,1,1,"Agua","Metro","https://www.metro.pe/otra","",false,true,false,true),new Store.Link(3,2,1,"Agua","Metro","https://www.metro.pe/","",false,true,false,true),new Store.Link(4,1,2,"Agua","Wong","https://www.wong.pe/","",false,true,false,true),new Store.Link(5,1,3,"Agua","Plaza","https://www.plazavea.com.pe/","",false,true,true,false));
  var model=new DashboardModel(List.of(active,inactive),List.of(external,disabled,own),links,List.of(new Store.AlertItem(1,"Agua","PRECIO_INFERIOR","PENDIENTE",""),new Store.AlertItem(2,"Agua","PRECIO_INFERIOR","ATENDIDA","")));
  assertEquals(1,model.monitoredProducts());assertEquals(Map.of(1L,1L),model.monitoredCounts());assertEquals(1,model.alertCounts().get("PENDIENTE"));assertEquals(1,model.alertCounts().get("ATENDIDA"));assertTrue(new DashboardModel(List.of(),List.of(),List.of(),List.of()).monitoredCounts().isEmpty());
 }
}
