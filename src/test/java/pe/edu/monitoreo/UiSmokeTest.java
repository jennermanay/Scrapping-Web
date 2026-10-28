package pe.edu.monitoreo;

import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.scene.image.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="UI_TESTS",matches="true")
class UiSmokeTest {
    @Test void setupLoginDemoHomologationComparisonAndReports()throws Exception {
        String url=System.getenv("INTEGRATION_DB_URL"),user=System.getenv("INTEGRATION_DB_USER"),password=System.getenv("INTEGRATION_DB_PASSWORD");
        String schema="ui_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("CREATE SCHEMA "+schema);}
        var store=new Store(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password);
        Platform.startup(()->{});Platform.setImplicitExit(false);var ref=new Object[]{null,null};var searching=new CountDownLatch(1);var release=new CountDownLatch(1);
        try {
            fx(()->{var app=new App(store,urlToLoad->PlazaVeaImporterTest.preview(),(product,site)->{searching.countDown();release.await(20,TimeUnit.SECONDS);return new SearchService.Result(product.name(),List.of(new Extractor.Quote(product.name(),new java.math.BigDecimal("5.30"),site+"producto-1/p","Búsqueda simulada para prueba de interfaz"),new Extractor.Quote("Leche GLORIA Caja 500ml",new java.math.BigDecimal("3.20"),site+"producto-2/p","Búsqueda simulada para prueba de interfaz")),List.of(site+"buscar?q=gloria"),4,List.of());});var stage=new Stage();ref[0]=app;ref[1]=stage;app.start(stage);return null;});
            Stage stage=(Stage)ref[1];awaitReady(stage);
            double loginWidth=fx(stage::getWidth);
            assertTrue(loginWidth<=720);assertTrue(fx(()->nodes(stage).stream().noneMatch(n->n instanceof TextField field&&"Correo".equals(field.getPromptText()))));
            fx(()->{Files.createDirectories(Path.of("artifacts"));snapshot(stage,Path.of("artifacts","interfaz-acceso-usuario.png"));return null;});
            fx(()->{text(stage,"Usuario").setText("operador_ui");text(stage,"Nombre del administrador").setText("Prueba de interfaz");text(stage,"Contraseña").setText("InterfazClave123");button(stage,"Crear administrador inicial").fire();return null;});
            awaitReady(stage);
            assertTrue(fx(()->button(stage,"Cerrar sesión")!=null));
            fx(()->{var tabs=(TabPane)nodes(stage).stream().filter(n->n instanceof TabPane).findFirst().orElseThrow();assertEquals("Inicio",tabs.getTabs().getFirst().getText());assertEquals("Inicio",tabs.getSelectionModel().getSelectedItem().getText());assertTrue(nodes(stage).stream().anyMatch(n->"plaza-vea-logo".equals(n.getId())));var pie=(javafx.scene.chart.PieChart)nodes(stage).stream().filter(n->"dashboard-alerts-chart".equals(n.getId())).findFirst().orElseThrow();assertTrue(pie.getData().isEmpty());return null;});
            assertTrue(fx(stage::getWidth)>loginWidth);
            assertTrue(fx(()->{var bounds=javafx.stage.Screen.getPrimary().getVisualBounds();return stage.getWidth()<=bounds.getWidth()&&stage.getHeight()<=bounds.getHeight();}));
            fx(()->{selectTab(stage,"Fuentes");
                @SuppressWarnings("unchecked") var detection=(ComboBox<String>)nodes(stage).stream().filter(n->"source-strategy".equals(n.getId())).findFirst().orElseThrow();
                assertEquals("AUTO",detection.getValue());var cssOptions=nodes(stage).stream().filter(n->"source-css-options".equals(n.getId())).findFirst().orElseThrow();assertFalse(cssOptions.isVisible());
                detection.setValue("CSS");assertTrue(cssOptions.isVisible());button(stage,"Nueva").fire();assertEquals("AUTO",detection.getValue());assertFalse(cssOptions.isManaged());
                ((TextField)nodes(stage).stream().filter(n->"source-name".equals(n.getId())).findFirst().orElseThrow()).setText("Fuente AUTO sin selectores");
                ((TextField)nodes(stage).stream().filter(n->"source-url".equals(n.getId())).findFirst().orElseThrow()).setText("https://fuente-auto.example/");button(stage,"Guardar fuente").fire();return null;});awaitReady(stage);
            assertTrue(store.sources().stream().anyMatch(f->f.name().equals("Fuente AUTO sin selectores")&&f.strategy().equals("AUTO")&&f.titleSelector().isEmpty()));
            fx(()->{snapshot(stage,Path.of("artifacts","interfaz-fuente-auto.png"));return null;});
            Samples.demo(store);var demoLink=store.links().stream().filter(l->l.url().equals("demo://producto")).findFirst().orElseThrow();var baseline=new Scraper(store,(source,page)->new Extractor.Quote("Producto demostración 1 L",new java.math.BigDecimal("120.00"),page,"Referencia de prueba")).run(demoLink);store.confirmAndCompare(store.candidatesForRun(demoLink.productId(),baseline.executionId()).getFirst().id(),"Misma marca y presentación");fx(()->{selectTab(stage,"Extracción y homologación");button(stage,"Actualizar catálogos").fire();return null;});awaitReady(stage);
            fx(()->{selectTab(stage,"Extracción y homologación");extractionProduct(stage).getSelectionModel().selectFirst();button(stage,"Iniciar búsqueda / extracción").fire();return null;});awaitScraping(stage);
            fx(()->{var table=candidateTable(stage);assertEquals("120.00",table.getColumns().stream().filter(c->c.getText().equals("Plaza Vea (S/)")).findFirst().orElseThrow().getCellObservableValue(0).getValue());assertEquals("105.00",table.getColumns().stream().filter(c->c.getText().equals("Precio capturado (S/)")).findFirst().orElseThrow().getCellObservableValue(0).getValue());text(stage,"Observación de homologación").setText("Marca Demo, presentación botella 1 L coincidente; dato ficticio");button(stage,"Confirmar y comparar").fire();return null;});awaitReady(stage);
            fx(()->{var popup=javafx.stage.Window.getWindows().stream().filter(w->w.getScene()!=null&&w.getScene().lookup("#price-alert-popup")!=null).findFirst().orElseThrow();var pane=(DialogPane)popup.getScene().lookup("#price-alert-popup");assertTrue(pane.getContentText().contains("-15.00"));snapshot((Stage)popup,Path.of("artifacts","interfaz-popup-alerta.png"));var view=pane.getButtonTypes().stream().filter(t->t.getText().equals("Ver alerta")).findFirst().orElseThrow();((Button)pane.lookupButton(view)).fire();return null;});awaitReady(stage);
            fx(()->{assertTrue(((TextArea)nodes(stage).stream().filter(n->"alert-detail".equals(n.getId())).findFirst().orElseThrow()).getText().contains("-15.00"));snapshot(stage,Path.of("artifacts","interfaz-alerta-detalle.png"));
                @SuppressWarnings("unchecked") var action=(ComboBox<PriceDecision.Action>)stage.getScene().lookup("#alert-price-action");action.setValue(PriceDecision.Action.MATCH);
                assertTrue(((Label)stage.getScene().lookup("#alert-price-simulation")).getText().contains("25.00"));snapshot(stage,Path.of("artifacts/interfaz-decision-precio.png"));((Button)stage.getScene().lookup("#save-alert-price")).fire();return null;});awaitReady(stage);
            assertEquals(new java.math.BigDecimal("105.00"),store.product(store.alertDetail(store.alerts().getFirst().id()).productId()).price());
            fx(()->{selectTab(stage,"Extracción y homologación");return null;});
            fx(()->{assertNotNull(candidateTable(stage).getSelectionModel().getSelectedItem().comparison());snapshot(stage,Path.of("artifacts","interfaz-comparacion-alerta.png"));button(stage,"Confirmar y comparar").fire();return null;});awaitReady(stage);
            assertEquals(1,store.alerts().size());
            fx(()->{selectTab(stage,"Historial y reportes");button(stage,"Consultar").fire();return null;});awaitReady(stage);
            assertTrue(fx(()->nodes(stage).stream().filter(n->n instanceof TableView<?>).map(n->(TableView<?>)n).anyMatch(t->!t.getItems().isEmpty())));
            fx(()->{Files.createDirectories(Path.of("artifacts"));snapshot(stage,Path.of("artifacts","interfaz-reportes.png"));selectTab(stage,"Productos");snapshot(stage,Path.of("artifacts","interfaz-productos.png"));return null;});
            assertEquals("Monitoreo competitivo de precios",fx(stage::getTitle));
            fx(()->{selectTab(stage,"Producto Plaza Vea");text(stage,"URL del producto de Plaza Vea").setText(Samples.PLAZA_URL);button(stage,"Obtener datos de Plaza Vea").fire();return null;});awaitReady(stage);
            assertEquals("6.10",fx(()->text(stage,"Precio propio obtenido de Plaza Vea").getText()));
            fx(()->{text(stage,"Costo unitario").setText("4.00");text(stage,"Condición del precio").setText("Precio web individual verificado en la prueba");nodes(stage).stream().filter(n->n instanceof CheckBox box&&box.getText().equals("Revisé los datos y las condiciones del producto de Plaza Vea")).map(n->(CheckBox)n).findFirst().orElseThrow().setSelected(true);snapshot(stage,Path.of("artifacts","interfaz-importar-plazavea.png"));button(stage,"Guardar producto").fire();return null;});awaitReady(stage);
            assertTrue(store.links().stream().anyMatch(Store.Link::reference));
            store.saveSource(new Store.Source(0,"Fuente alternativa","https://monitoreo.example/categoria","","","","","AUTO",60,24,true));
            store.saveSource(new Store.Source(0,"Fuente elegida","https://monitoreo.example/","","","","","AUTO",60,24,true));
            var chosen=store.sources().stream().filter(f->f.name().equals("Fuente elegida")).findFirst().orElseThrow();
            var alternative=store.sources().stream().filter(f->f.name().equals("Fuente alternativa")).findFirst().orElseThrow();
            fx(()->{button(stage,"Actualizar productos y fuentes").fire();return null;});awaitReady(stage);
            fx(()->{var table=(TableView<?>)nodes(stage).stream().filter(n->"monitoring-products".equals(n.getId())).findFirst().orElseThrow();assertFalse(table.getItems().isEmpty());
                button(stage,"Actualizar productos y fuentes").requestFocus();nodes(stage);
                var selectedRow=table.lookupAll(".table-row-cell").stream().filter(n->n instanceof TableRow<?> row&&row.isSelected()&&!row.isEmpty()).map(n->(TableRow<?>)n).findFirst().orElseThrow();
                assertEquals(javafx.scene.paint.Color.web("#cc292e"),selectedRow.getBackground().getFills().getFirst().getFill());
                var box=(CheckBox)nodes(stage).stream().filter(n->("include-source-"+chosen.id()).equals(n.getId())).findFirst().orElseThrow();box.setSelected(true);((CheckBox)nodes(stage).stream().filter(n->("include-source-"+alternative.id()).equals(n.getId())).findFirst().orElseThrow()).setSelected(true);
                @SuppressWarnings("unchecked") var frequency=(Spinner<Integer>)nodes(stage).stream().filter(n->"monitoring-frequency".equals(n.getId())).findFirst().orElseThrow();frequency.getValueFactory().setValue(2);
                snapshot(stage,Path.of("artifacts","interfaz-fuentes-incluidas.png"));button(stage,"Guardar fuentes del producto").fire();return null;});awaitReady(stage);
            fx(()->{button(stage,"Ir a extracción").fire();button(stage,"Iniciar búsqueda / extracción").fire();return null;});
            assertTrue(searching.await(5,TimeUnit.SECONDS));
            fx(()->{assertFalse(stage.getScene().getRoot().isDisabled());assertTrue(stage.getScene().lookup("#scraping-in-progress").isVisible());selectTab(stage,"Usuarios");return null;});awaitReady(stage);
            fx(()->{var users=(ListView<?>)stage.getScene().lookup("#users-list");assertTrue(users.getItems().stream().anyMatch(u->u.toString().contains("operador_ui")));selectTab(stage,"Extracción y homologación");assertTrue(stage.getScene().lookup("#scraping-in-progress").isVisible());snapshot(stage,Path.of("artifacts/interfaz-scraping-segundo-plano.png"));return null;});
            release.countDown();awaitScraping(stage);
            fx(()->{var combo=extractionProduct(stage);combo.show();return null;});
            fx(()->{var popup=javafx.stage.Window.getWindows().stream().filter(w->w!=stage&&w.isShowing()&&w.getScene()!=null&&w.getScene().getRoot().lookup(".list-cell")!=null).findFirst().orElseThrow();popup.getScene().getRoot().applyCss();
                var cell=popup.getScene().getRoot().lookupAll(".list-cell").stream().filter(n->n instanceof ListCell<?> c&&!c.isEmpty()&&!c.isSelected()).map(n->(ListCell<?>)n).findFirst().orElseThrow();
                cell.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"),true);popup.getScene().getRoot().applyCss();assertEquals(javafx.scene.paint.Color.web("#26374a"),cell.getTextFill());assertEquals(javafx.scene.paint.Color.web("#f5dce0"),cell.getBackground().getFills().getFirst().getFill());extractionProduct(stage).hide();return null;});
            fx(()->{assertEquals(4,candidateTable(stage).getItems().size());var selector=(ComboBox<?>)nodes(stage).stream().filter(n->"extraction-source".equals(n.getId())).findFirst().orElseThrow();assertEquals("Todas las fuentes",selector.getValue().toString());selector.getSelectionModel().select(selector.getItems().stream().map(Object::toString).toList().indexOf("Fuente elegida"));return null;});
            fx(()->{assertEquals(1,((TableView<?>)nodes(stage).stream().filter(n->"source-run-results".equals(n.getId())).findFirst().orElseThrow()).getItems().size());assertTrue(candidateTable(stage).getItems().stream().allMatch(c->c.source().equals("Fuente elegida")));snapshot(stage,Path.of("artifacts","interfaz-extraccion-por-producto.png"));return null;});
            var searchLink=store.links().stream().filter(l->l.url().equals("https://monitoreo.example/")&&l.search()).findFirst().orElseThrow();assertEquals(chosen.id(),searchLink.sourceId());assertEquals(4,store.candidates(searchLink.productId()).size());assertEquals(2,store.monitoringSettings().get(searchLink.productId()).minutes());assertTrue(store.candidates(searchLink.productId()).stream().allMatch(c->c.status().equals("PENDIENTE")));
            fx(()->{var table=candidateTable(stage);var exact=table.getItems().stream().filter(c->c.title().contains("946ml")).findFirst().orElseThrow();table.getSelectionModel().select(exact);text(stage,"Observación de homologación").setText("GLORIA, caja 946 ml, entera UHT, precio individual verificado");button(stage,"Confirmar y comparar").fire();return null;});awaitReady(stage);
            var approval=store.homologated(searchLink.productId(),searchLink.sourceId()).getFirst();new Scraper(store,(source,page)->new Extractor.Quote(approval.title(),new java.math.BigDecimal("5.20"),page,"Variación de precio de prueba")).run(searchLink,approval);
            fx(()->{button(stage,"Actualizar catálogos").fire();return null;});awaitReady(stage);
            fx(()->{selectTab(stage,"Productos Homologados");return null;});awaitReady(stage);
            fx(()->{var table=(TableView<?>)stage.getScene().lookup("#homologated-products");assertTrue(table.getItems().size()>=2);snapshot(stage,Path.of("artifacts/interfaz-productos-homologados.png"));return null;});
            fx(()->{selectTab(stage,"Inicio");assertTrue(nodes(stage).stream().noneMatch(n->n instanceof Label l&&l.getText().equals("Fuentes registradas")));var pie=(javafx.scene.chart.PieChart)nodes(stage).stream().filter(n->"dashboard-alerts-chart".equals(n.getId())).findFirst().orElseThrow();assertEquals(2.0,pie.getData().stream().mapToDouble(javafx.scene.chart.PieChart.Data::getPieValue).sum());assertTrue(nodes(stage).stream().anyMatch(n->"dashboard-coverage-chart".equals(n.getId())));snapshot(stage,Path.of("artifacts","interfaz-inicio-resumen.png"));
                var cards=nodes(stage).stream().filter(n->n.getStyleClass().contains("metric-card")).toList();assertEquals(4,cards.size());assertEquals(1,cards.stream().map(n->n.getBoundsInParent().getMinY()).distinct().count());
                return null;});
            fx(()->{stage.setWidth(940);return null;});awaitReady(stage);
            fx(()->{var cards=nodes(stage).stream().filter(n->n.getStyleClass().contains("metric-card")).toList();assertEquals(2,cards.stream().map(n->n.getBoundsInParent().getMinY()).distinct().count());snapshot(stage,Path.of("artifacts","interfaz-inicio-compacto.png"));return null;});
            assertEquals(2,store.alerts().size());assertTrue(store.candidates(searchLink.productId()).stream().anyMatch(c->c.comparison()!=null&&c.comparison().alertType().equals("BAJADA_PRECIO")));
            fx(()->{selectTab(stage,"Roles");var list=(ListView<?>)nodes(stage).stream().filter(n->"roles-list".equals(n.getId())).findFirst().orElseThrow();assertEquals(2,list.getItems().size());snapshot(stage,Path.of("artifacts","interfaz-roles.png"));list.getSelectionModel().select(list.getItems().stream().map(Object::toString).toList().indexOf("Analista"));((TextField)nodes(stage).stream().filter(n->"role-name".equals(n.getId())).findFirst().orElseThrow()).setText("Analista comercial");button(stage,"Guardar rol").fire();return null;});awaitReady(stage);
            assertEquals("Analista comercial",store.roles().stream().filter(r->r.code().equals("ANALISTA")).findFirst().orElseThrow().name());
            store.saveUser(0,"analista_ui","Analista UI","AnalistaClave123","ANALISTA",true);
            fx(()->{button(stage,"Cerrar sesión").fire();assertTrue(nodes(stage).stream().noneMatch(n->n instanceof Button b&&b.getText().equals("Configuración de conexión")));text(stage,"Usuario").setText("analista_ui");text(stage,"Contraseña").setText("AnalistaClave123");button(stage,"Ingresar").fire();return null;});awaitReady(stage);
            fx(()->{var tabs=(TabPane)nodes(stage).stream().filter(n->n instanceof TabPane).findFirst().orElseThrow();assertEquals(Set.of("Producto Plaza Vea","Productos","Extracción y homologación","Alertas"),tabs.getTabs().stream().map(Tab::getText).collect(java.util.stream.Collectors.toSet()));selectTab(stage,"Productos");snapshot(stage,Path.of("artifacts","interfaz-analista.png"));return null;});

        } finally {
            release.countDown();fx(()->{if(ref[0]!=null)((App)ref[0]).stop();if(ref[1]!=null)((Stage)ref[1]).close();return null;});Platform.exit();
            try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}
        }
    }
    private static void awaitScraping(Stage stage)throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(System.nanoTime()<until){if(fx(()->{var n=stage.getScene().lookup("#scraping-in-progress");return n!=null&&!n.isVisible();})){awaitReady(stage);return;}Thread.sleep(100);}fail("Scraping no terminó.");}
    private static void awaitReady(Stage stage)throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);do{Thread.sleep(100);if(fx(()->!stage.getScene().getRoot().isDisabled())){Thread.sleep(100);if(fx(()->!stage.getScene().getRoot().isDisabled()))return;}}while(System.nanoTime()<until);fail("La interfaz no terminó la operación a tiempo.");}
    private static <T>T fx(Callable<T> action)throws Exception {var future=new FutureTask<T>(action);Platform.runLater(future);return future.get(35,TimeUnit.SECONDS);}
    private static List<Node> nodes(Stage stage){stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();var out=new ArrayList<Node>();collect(stage.getScene().getRoot(),out);return out;}
    private static void collect(Node n,List<Node> out){out.add(n);if(n instanceof Parent p)for(Node child:p.getChildrenUnmodifiable())collect(child,out);}
    private static boolean treeVisible(Node node){for(Node current=node;current!=null;current=current.getParent())if(!current.isVisible())return false;return true;}
    private static TextField text(Stage stage,String prompt){return nodes(stage).stream().filter(n->treeVisible(n)&&n instanceof TextField t&&prompt.equals(t.getPromptText())).map(n->(TextField)n).findFirst().orElseThrow();}
    @SuppressWarnings("unchecked") private static ComboBox<Store.Product> extractionProduct(Stage stage){return (ComboBox<Store.Product>)nodes(stage).stream().filter(n->"extraction-product".equals(n.getId())).findFirst().orElseThrow();}
    private static Button button(Stage stage,String label){return nodes(stage).stream().filter(n->treeVisible(n)&&n instanceof Button b&&b.getText().equals(label)).map(n->(Button)n).findFirst().orElseThrow();}
    private static List<ListView<?>> lists(Stage stage){var out=new ArrayList<ListView<?>>();for(var n:nodes(stage))if(n instanceof ListView<?> list)out.add(list);return out;}
    @SuppressWarnings("unchecked") private static TableView<Store.Candidate> candidateTable(Stage stage){return (TableView<Store.Candidate>)nodes(stage).stream().filter(n->n instanceof TableView<?> && "candidate-comparisons".equals(n.getId())).findFirst().orElseThrow();}
    private static void selectTab(Stage stage,String text){var pane=nodes(stage).stream().filter(n->n instanceof TabPane).map(n->(TabPane)n).findFirst().orElseThrow();pane.getSelectionModel().select(pane.getTabs().stream().filter(t->t.getText().equals(text)).findFirst().orElseThrow());stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();}
    private static void snapshot(Stage stage,Path path)throws Exception {WritableImage image=stage.getScene().snapshot(null);int w=(int)image.getWidth(),h=(int)image.getHeight();int[] pixels=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),pixels,0,w);var buffered=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);buffered.setRGB(0,0,w,h,pixels,0,w);ImageIO.write(buffered,"png",path.toFile());}
}

