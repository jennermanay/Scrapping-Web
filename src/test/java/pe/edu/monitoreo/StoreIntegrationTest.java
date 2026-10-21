package pe.edu.monitoreo;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.sql.*;
import java.nio.file.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="INTEGRATION_DB_URL",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StoreIntegrationTest {
    Store store;String url,user,password,schema;Store.Link link;
    @TempDir Path dir;
    @BeforeAll void setup()throws Exception {
        url=System.getenv("INTEGRATION_DB_URL");user=System.getenv("INTEGRATION_DB_USER");password=System.getenv("INTEGRATION_DB_PASSWORD");
        schema="test_"+java.util.UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("CREATE SCHEMA "+schema);}
        store=new Store(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password);store.initialize();store.setup("admin@example.com","Administrador","PruebaSegura123");store.login("admin@example.com","PruebaSegura123");Samples.demo(store);link=store.links().getFirst();
    }
    @BeforeEach void reset()throws Exception {
        store.login("admin@example.com","PruebaSegura123");var p=store.product(link.productId());store.saveProduct(new Store.Product(p.id(),p.code(),p.name(),p.brand(),p.model(),p.presentation(),p.capacity(),p.category(),p.keywords(),new BigDecimal("80"),new BigDecimal("120"),true));
    }
    @AfterAll void cleanup()throws Exception {if(schema!=null)try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}}
    long capture(String price)throws Exception {new Scraper(store,(s,u)->new Extractor.Quote("Producto demostración 1 L",new BigDecimal(price),u,"Dato controlado de prueba")).run(link);return store.candidates(link.productId()).getFirst().id();}
    long confirmed(String price)throws Exception {long id=capture(price);store.validate(id,true,"Marca Demo, botella 1 L y condición verificadas en fixture");return id;}
    @Test void CP01_registerAndPersist()throws Exception {assertEquals(0,new BigDecimal("80").compareTo(store.product(link.productId()).cost()));assertFalse(store.sources().isEmpty());}
    @Test void CP02_rejectNegativeCost()throws Exception {var p=store.product(link.productId());assertThrows(IllegalArgumentException.class,()->store.saveProduct(new Store.Product(0,"INVALIDO","Producto","Demo","","Botella","1 L","General","",new BigDecimal("-1"),BigDecimal.TEN,true)));}
    @Test void CP03_lowerCompetitor()throws Exception {var r=store.compare(confirmed("105"));assertEquals(new BigDecimal("23.81"),r.simulatedMargin());assertTrue(r.lower());assertTrue(store.alerts().stream().anyMatch(a->a.type().equals("PRECIO_INFERIOR")));}
    @Test void CP04_higherCompetitor()throws Exception {int count=store.alerts().size();assertFalse(store.compare(confirmed("130")).lower());assertEquals(count,store.alerts().size());}
    @Test void CP05_loss()throws Exception {assertTrue(store.compare(confirmed("70")).loss());assertTrue(store.alerts().stream().anyMatch(a->a.type().equals("PERDIDA")));}
    @Test void CP06_rejectDifferentPresentation()throws Exception {long id=capture("105");assertThrows(IllegalArgumentException.class,()->store.compare(id));store.validate(id,false,"La ficha contiene 500 ml y el producto propio 1 L");assertThrows(IllegalArgumentException.class,()->store.compare(id));}
    @Test void CP07_timeoutPreservesHistory()throws Exception {int before=store.report(ReportType.HISTORIAL,ReportFilter.all()).rows().size();assertThrows(java.net.SocketTimeoutException.class,()->new Scraper(store,(s,u)->{throw new java.net.SocketTimeoutException("Timeout controlado");}).run(link));assertEquals(before,store.report(ReportType.HISTORIAL,ReportFilter.all()).rows().size());var report=store.report(ReportType.EJECUCIONES,ReportFilter.all());assertTrue(report.rows().stream().anyMatch(r->r.contains("ERROR")));}
    @Test void snapshotsAndDuplicates()throws Exception {long id=confirmed("105");store.compare(id);var p=store.product(link.productId());store.saveProduct(new Store.Product(p.id(),p.code(),p.name(),p.brand(),p.model(),p.presentation(),p.capacity(),p.category(),p.keywords(),new BigDecimal("90"),new BigDecimal("125"),true));try(var c=store.open();var s=c.prepareStatement("SELECT costo,propio FROM comparacion WHERE resultado_id=?")){s.setLong(1,id);try(var r=s.executeQuery()){assertTrue(r.next());assertEquals(0,new BigDecimal("80").compareTo(r.getBigDecimal(1)));assertEquals(0,new BigDecimal("120").compareTo(r.getBigDecimal(2)));}}assertThrows(IllegalStateException.class,()->store.compare(id));}
    @Test void expiry()throws Exception {long id=confirmed("105");try(var c=store.open();var s=c.prepareStatement("UPDATE resultado SET fecha=CURRENT_TIMESTAMP-interval '8 days' WHERE id=?")){s.setLong(1,id);s.executeUpdate();}assertThrows(IllegalArgumentException.class,()->store.compare(id));}
    @Test void authorizationAndAuditedAttention()throws Exception {
        store.compare(confirmed("105"));var alert=store.alerts().getFirst();store.attend(alert.id(),"ATENDIDA","Se conserva el precio propio tras revisar margen");assertTrue(store.alerts().stream().anyMatch(a->a.id()==alert.id()&&a.status().equals("ATENDIDA")));
        if(store.users().stream().noneMatch(u->u.email().equals("analista@example.com")))store.saveUser(0,"analista@example.com","Analista","AnalistaClave123","ANALISTA",true);
        store.login("analista@example.com","AnalistaClave123");assertFalse(store.session().admin());assertThrows(SecurityException.class,()->store.saveSource(store.source(link.sourceId())));assertFalse(store.products().isEmpty());store.logout();assertThrows(SecurityException.class,store::products);
    }
    @Test void reportsAndFilters()throws Exception {store.compare(confirmed("105"));for(var type:ReportType.values())assertNotNull(store.report(type,ReportFilter.all()));var filter=new ReportFilter(link.productId(),link.sourceId(),LocalDate.now(ZoneId.of("America/Lima")).minusDays(1),LocalDate.now(ZoneId.of("America/Lima")));var data=store.report(ReportType.COMPARACIONES,filter);assertFalse(data.rows().isEmpty());var path=dir.resolve("comparaciones.pdf");Reports.pdf(data,path);store.recordExport(ReportType.COMPARACIONES,filter,path.toString());assertFalse(store.report(ReportType.EXPORTACIONES,ReportFilter.all()).rows().isEmpty());assertThrows(IllegalArgumentException.class,()->store.report(ReportType.HISTORIAL,new ReportFilter(0,0,LocalDate.now(),LocalDate.now().minusDays(1))));}
    @Test void idempotentMigration()throws Exception {int count=store.products().size();store.initialize();assertEquals(count,store.products().size());assertThrows(IllegalStateException.class,()->store.setup("otro@example.com","Otro","OtraClave12345"));}
    @Test void disabledLinkAndSchedule()throws Exception {store.saveLink(link.id(),link.productId(),link.sourceId(),link.url(),link.condition(),true,false);try{assertThrows(IllegalArgumentException.class,()->new Scraper(store).run(link));assertTrue(store.dueLinks().stream().noneMatch(l->l.id()==link.id()));}finally{store.saveLink(link.id(),link.productId(),link.sourceId(),link.url(),link.condition(),false,true);}}
    @Test void scheduledRunAdvancesNextExecution()throws Exception {
        store.saveLink(link.id(),link.productId(),link.sourceId(),link.url(),link.condition(),true,true);
        try {try(var c=store.open();var s=c.createStatement()){s.execute("UPDATE producto_fuente SET proxima_ejecucion=CURRENT_TIMESTAMP-interval '1 minute' WHERE id="+link.id());}
            assertTrue(store.dueLinks().stream().anyMatch(l->l.id()==link.id()));new Scraper(store).run(link);assertTrue(store.dueLinks().stream().noneMatch(l->l.id()==link.id()));
        }finally{store.saveLink(link.id(),link.productId(),link.sourceId(),link.url(),link.condition(),false,true);}
    }
    @Test @EnabledIfEnvironmentVariable(named="LIVE_TESTS",matches="true") void realPlazaVeaEndToEnd()throws Exception {
        Samples.realPages(store);var real=store.links().stream().filter(l->l.url().equals(Samples.PLAZA_URL)).findFirst().orElseThrow();new Scraper(store).run(real);var candidate=store.candidates(real.productId()).getFirst();assertTrue(candidate.title().toUpperCase().contains("GLORIA"));assertTrue(candidate.price().signum()>0);assertTrue(candidate.url().startsWith("https://www.plazavea.com.pe/"));
        // Test validates the configured 946 ml example; production still requires the human reviewer.
        assertTrue(candidate.title().contains("946"));store.validate(candidate.id(),true,"Prueba viva: título GLORIA Entera UHT Caja 946ml coincide con el ejemplo. Costo y precio propio son de prueba.");store.compare(candidate.id());
        var data=store.report(ReportType.COMPARACIONES,new ReportFilter(real.productId(),real.sourceId(),null,null));assertEquals(1,data.rows().size());Files.createDirectories(Path.of("artifacts"));Reports.csv(data,Path.of("artifacts","prueba-real.csv"));Reports.pdf(data,Path.of("artifacts","prueba-real.pdf"));
        System.out.println("PRUEBA REAL: "+candidate.title()+" | PEN "+candidate.price()+" | "+candidate.date()+" | "+candidate.url());
        var makro=store.links().stream().filter(l->l.url().equals(Samples.MAKRO_URL)).findFirst().orElseThrow();new Scraper(store).run(makro);var other=store.candidates(makro.productId()).getFirst();assertTrue(other.title().contains("946"));assertTrue(other.price().signum()>0);store.validate(other.id(),true,"Prueba viva de segunda fuente: GLORIA Entera UHT Caja 946ml; verificar condiciones de canal. Valores propios de prueba.");store.compare(other.id());
        var both=store.report(ReportType.COMPARACIONES,new ReportFilter(real.productId(),0,null,null));assertEquals(2,both.rows().size());Reports.csv(both,Path.of("artifacts","prueba-real.csv"));Reports.pdf(both,Path.of("artifacts","prueba-real.pdf"));
        System.out.println("PRUEBA REAL MAKRO: "+other.title()+" | PEN "+other.price()+" | "+other.url());
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(Path.of("artifacts","prueba-real.pdf").toFile())){
            var renderer=new org.apache.pdfbox.rendering.PDFRenderer(pdf);
            javax.imageio.ImageIO.write(renderer.renderImageWithDPI(0,110),"png",Path.of("artifacts","reporte-real-vista.png").toFile());
        }
    }
}
