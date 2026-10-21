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
        Platform.startup(()->{});Platform.setImplicitExit(false);var ref=new Object[]{null,null};
        try {
            fx(()->{var app=new App(store);var stage=new Stage();ref[0]=app;ref[1]=stage;app.start(stage);return null;});
            Stage stage=(Stage)ref[1];awaitReady(stage);
            fx(()->{text(stage,"Correo").setText("ui@example.com");text(stage,"Nombre del administrador").setText("Prueba de interfaz");text(stage,"Contraseña (10 caracteres como mínimo)").setText("InterfazClave123");button(stage,"Crear administrador inicial").fire();return null;});
            awaitReady(stage);
            assertTrue(fx(()->button(stage,"Cerrar sesión")!=null));
            fx(()->{button(stage,"Cargar ejemplo de prueba").fire();return null;});awaitReady(stage);
            fx(()->{selectTab(stage,"Extracción y homologación");var list=lists(stage).stream().filter(l->!l.getItems().isEmpty()&&l.getItems().getFirst() instanceof Store.Link).findFirst().orElseThrow();list.getSelectionModel().selectFirst();button(stage,"Extraer ficha seleccionada").fire();return null;});awaitReady(stage);
            fx(()->{var list=lists(stage).stream().filter(l->!l.getItems().isEmpty()&&l.getItems().getFirst() instanceof Store.Candidate).findFirst().orElseThrow();list.getSelectionModel().selectFirst();text(stage,"Describe las coincidencias o diferencias de marca, presentación, capacidad y oferta").setText("Marca Demo, presentación botella 1 L coincidente; dato ficticio");button(stage,"Confirmar equivalencia").fire();return null;});awaitReady(stage);
            fx(()->{var list=lists(stage).stream().filter(l->!l.getItems().isEmpty()&&l.getItems().getFirst() instanceof Store.Candidate).findFirst().orElseThrow();list.getSelectionModel().selectFirst();button(stage,"Comparar y generar alerta").fire();return null;});awaitReady(stage);
            assertEquals(1,store.alerts().size());
            fx(()->{selectTab(stage,"Historial y reportes");button(stage,"Consultar").fire();return null;});awaitReady(stage);
            assertTrue(fx(()->nodes(stage).stream().filter(n->n instanceof TableView<?>).map(n->(TableView<?>)n).anyMatch(t->!t.getItems().isEmpty())));
            fx(()->{Files.createDirectories(Path.of("artifacts"));snapshot(stage,Path.of("artifacts","interfaz-reportes.png"));selectTab(stage,"Productos");snapshot(stage,Path.of("artifacts","interfaz-productos.png"));return null;});
            assertEquals("Monitoreo competitivo de precios",fx(stage::getTitle));
            fx(()->{button(stage,"Cerrar sesión").fire();button(stage,"Configuración de conexión").fire();button(stage,"Probar conexión").fire();return null;});awaitReady(stage);
            assertTrue(fx(()->nodes(stage).stream().anyMatch(n->n instanceof Label l&&l.getText().startsWith("Conexión correcta"))));
            fx(()->{snapshot(stage,Path.of("artifacts","interfaz-conexion.png"));return null;});
        } finally {
            fx(()->{if(ref[0]!=null)((App)ref[0]).stop();if(ref[1]!=null)((Stage)ref[1]).close();return null;});Platform.exit();
            try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}
        }
    }
    private static void awaitReady(Stage stage)throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);do{Thread.sleep(100);if(fx(()->!stage.getScene().getRoot().isDisabled())){Thread.sleep(100);if(fx(()->!stage.getScene().getRoot().isDisabled()))return;}}while(System.nanoTime()<until);fail("La interfaz no terminó la operación a tiempo.");}
    private static <T>T fx(Callable<T> action)throws Exception {var future=new FutureTask<T>(action);Platform.runLater(future);return future.get(35,TimeUnit.SECONDS);}
    private static List<Node> nodes(Stage stage){stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();var out=new ArrayList<Node>();collect(stage.getScene().getRoot(),out);return out;}
    private static void collect(Node n,List<Node> out){out.add(n);if(n instanceof Parent p)for(Node child:p.getChildrenUnmodifiable())collect(child,out);}
    private static TextField text(Stage stage,String prompt){return nodes(stage).stream().filter(n->n instanceof TextField t&&prompt.equals(t.getPromptText())).map(n->(TextField)n).findFirst().orElseThrow();}
    private static Button button(Stage stage,String label){return nodes(stage).stream().filter(n->n instanceof Button b&&b.getText().equals(label)).map(n->(Button)n).findFirst().orElseThrow();}
    private static List<ListView<?>> lists(Stage stage){var out=new ArrayList<ListView<?>>();for(var n:nodes(stage))if(n instanceof ListView<?> list)out.add(list);return out;}
    private static void selectTab(Stage stage,String text){var pane=nodes(stage).stream().filter(n->n instanceof TabPane).map(n->(TabPane)n).findFirst().orElseThrow();pane.getSelectionModel().select(pane.getTabs().stream().filter(t->t.getText().equals(text)).findFirst().orElseThrow());stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();}
    private static void snapshot(Stage stage,Path path)throws Exception {WritableImage image=stage.getScene().snapshot(null);int w=(int)image.getWidth(),h=(int)image.getHeight();int[] pixels=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),pixels,0,w);var buffered=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);buffered.setRGB(0,0,w,h,pixels,0,w);ImageIO.write(buffered,"png",path.toFile());}
}
