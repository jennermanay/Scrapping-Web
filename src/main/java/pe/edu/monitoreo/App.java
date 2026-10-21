package pe.edu.monitoreo;

import javafx.application.Application;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.concurrent.Task;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public class App extends Application {
    private final Store store;
    public App(){this(new Store());}
    App(Store store){this.store=store;}
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"monitoreo-worker");t.setDaemon(true);return t;});
    private final BorderPane root=new BorderPane();
    private final Label status=new Label("Preparando base de datos…");
    private final ProgressIndicator progress=new ProgressIndicator();
    private final CheckBox scheduler=new CheckBox("Monitoreo programado durante esta sesión");
    private final ComboBox<Store.Product> productPick=new ComboBox<>();
    private final ComboBox<Store.Source> sourcePick=new ComboBox<>();
    private final ComboBox<Store.Product> analysisProduct=new ComboBox<>();
    private final ListView<Store.Product> productList=new ListView<>();
    private final ListView<Store.Source> sourceList=new ListView<>();
    private final ListView<Store.Link> linkList=new ListView<>();
    private final ListView<Store.Link> runLinks=new ListView<>();
    private final ListView<Store.Candidate> candidates=new ListView<>();
    private final ListView<Store.AlertItem> alerts=new ListView<>();
    private final ComboBox<Store.Product> reportProduct=new ComboBox<>();
    private final ComboBox<Store.Source> reportSource=new ComboBox<>();
    private final Timeline ticker=new Timeline(new KeyFrame(Duration.seconds(60),e->scheduled()));
    private final Label summary=new Label();
    private boolean busy;
    private Stage stage;
    private record Catalog(List<Store.Product> products,List<Store.Source> sources,List<Store.Link> links,List<Store.AlertItem> alerts){}
    @Override public void start(Stage stage){
        this.stage=stage;progress.setPrefSize(22,22);progress.setVisible(false);
        var footer=new HBox(10,progress,status);footer.setPadding(new Insets(10));root.setBottom(footer);
        stage.setTitle("Monitoreo competitivo de precios");stage.setScene(new Scene(root,1250,850));stage.show();
        ticker.setCycleCount(Timeline.INDEFINITE);
        connectionScreen();
        work(()->{store.initialize();return store.needsSetup();},this::loginScreen);
    }
    private void connectionScreen(){
        root.setTop(null);scheduler.setSelected(false);ticker.stop();
        root.setCenter(column(new Label("Conexión PostgreSQL"),new Label("Archivo editable: "+Config.file()),
            new Label("Edita DB_URL, DB_USER y DB_PASSWORD. Guarda el archivo y pulsa Probar conexión."),
            new Label("Probar conexión solo valida el acceso. Conectar y abrir también prepara las tablas del sistema."),
            flow(button("Probar conexión",()->work(()->{store.reloadConfiguration();return store.testConnection();},status::setText)),
                button("Conectar y abrir",()->work(()->{store.reloadConfiguration();store.initialize();return store.needsSetup();},this::loginScreen)))));
    }
    private void loginScreen(boolean setup){
        root.setTop(null);scheduler.setSelected(false);ticker.stop();
        var email=field("Correo");var name=field("Nombre del administrador");var password=new PasswordField();password.setPromptText("Contraseña (10 caracteres como mínimo)");
        var login=button(setup?"Crear administrador inicial":"Ingresar",()->{
            String e=required(email),p=password.getText(),n=name.getText();
            work(()->{if(setup)store.setup(e,n,p);return store.login(e,p);},u->{password.clear();mainScreen();refresh();});
        });
        var box=column(new Label(setup?"Primer inicio · Crea tu cuenta de administrador":"Acceso al sistema"),email,password,login,
            button("Configuración de conexión",()->{store.logout();clearLists();connectionScreen();}));
        if(setup)box.getChildren().add(2,name);
        box.setMaxWidth(440);root.setCenter(box);BorderPane.setMargin(box,new Insets(50));
        status.setText("Conexión preparada. Credenciales personales; no hay contraseñas predeterminadas.");
    }
    private void mainScreen(){
        var user=store.session();var tabs=new TabPane();tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(tab("Inicio",home()));
        if(user.admin()){tabs.getTabs().add(tab("Productos",products()));tabs.getTabs().add(tab("Fuentes",sources()));tabs.getTabs().add(tab("Fichas a monitorear",links()));}
        tabs.getTabs().add(tab("Extracción y homologación",analysis()));tabs.getTabs().add(tab("Alertas",alerts()));tabs.getTabs().add(tab("Historial y reportes",reports()));
        if(user.admin())tabs.getTabs().add(tab("Usuarios",users()));
        var logout=button("Cerrar sesión",()->{ticker.stop();scheduler.setSelected(false);store.logout();clearLists();loginScreen(false);});
        var header=new HBox(18,new Label("Monitoreo de precios"),new Label(user.toString()),logout);header.setPadding(new Insets(14));root.setTop(header);root.setCenter(tabs);
        ticker.play();
    }
    private Node home(){
        var demo=button("Cargar ejemplo de prueba",()->work(()->{Samples.demo(store);return catalog();},this::apply));
        var real=button("Cargar fichas reales: Plaza Vea y Makro",()->work(()->{Samples.realPages(store);return catalog();},c->{apply(c);status.setText("Fichas reales cargadas. Costo y precio propio son ejemplos editables; revisa ubicación y condiciones antes de comparar.");}));
        demo.setVisible(store.session().admin());demo.setManaged(store.session().admin());real.setVisible(store.session().admin());real.setManaged(store.session().admin());
        return column(new Label("Panel principal"),summary,
            new Label("1. Registra productos y fuentes. 2. Vincula una ficha real. 3. Extrae y revisa. 4. Confirma equivalencia. 5. Compara y atiende alertas."),
            new Label("Los datos externos conservan URL, fecha y evidencia. El sistema no modifica precios propios automáticamente."),
            flow(button("Actualizar panel",this::refresh),demo,real),scheduler,
            new Label("La programación procesa vínculos activos cada 60 s cuando llega su próxima ejecución (intervalo por fuente: mínimo 15 min)."),
            new Label("Funciona mientras la aplicación y la sesión permanezcan abiertas. Una falla queda registrada en el reporte de ejecuciones."));
    }
    private Node products(){
        var code=field("Código único");var name=field("Nombre");var brand=field("Marca");var model=field("Modelo / variedad");var presentation=field("Presentación");var capacity=field("Capacidad con unidad");var category=field("Categoría");category.setText("General");var keywords=field("Palabras clave separadas por coma");var cost=field("Costo unitario PEN");var price=field("Precio propio PEN");var active=check("Activo",true);
        productList.setPrefHeight(230);productList.getSelectionModel().selectedItemProperty().addListener((o,a,p)->{if(p==null)return;code.setText(p.code());name.setText(p.name());brand.setText(p.brand());model.setText(p.model());presentation.setText(p.presentation());capacity.setText(p.capacity());category.setText(p.category());keywords.setText(p.keywords());cost.setText(p.cost().toPlainString());price.setText(p.price().toPlainString());active.setSelected(p.active());});
        var save=button("Guardar producto",()->{
            var p=productList.getSelectionModel().getSelectedItem();var data=new Store.Product(p==null?0:p.id(),required(code),required(name),required(brand),model.getText().trim(),required(presentation),required(capacity),required(category),keywords.getText(),PriceParser.parse(cost.getText()),PriceParser.parse(price.getText()),active.isSelected());
            work(()->{store.saveProduct(data);return catalog();},this::apply);
        });
        var clear=button("Nuevo",()->{productList.getSelectionModel().clearSelection();for(var f:List.of(code,name,brand,model,presentation,capacity,keywords,cost,price))f.clear();category.setText("General");active.setSelected(true);});
        return column(new Label("Catálogo de productos · Selecciona para editar; desmarca Activo para desactivar"),productList,
            form("Código",code,"Nombre",name,"Marca",brand,"Modelo / variedad",model,"Presentación",presentation,"Capacidad",capacity,"Categoría",category,"Palabras clave",keywords,"Costo PEN",cost,"Precio propio PEN",price),flow(active,save,clear));
    }
    private Node sources(){
        var name=field("Nombre");var url=field("URL base del dominio");var title=field("Selector CSS título");var price=field("Selector CSS precio");var titleAttr=field("Vacío = texto; ejemplo: content");var priceAttr=field("Vacío = texto; ejemplo: content");var strategy=new ComboBox<String>();strategy.getItems().setAll("CSS","VTEX","JSON_LD","DEMO");strategy.setValue("CSS");var interval=new Spinner<Integer>(15,1440,60,15);var validity=new Spinner<Integer>(1,168,24);var active=check("Activa",true);
        sourceList.setPrefHeight(210);sourceList.getSelectionModel().selectedItemProperty().addListener((o,a,s)->{if(s==null)return;name.setText(s.name());url.setText(s.url());title.setText(s.titleSelector());price.setText(s.priceSelector());titleAttr.setText(s.titleAttribute());priceAttr.setText(s.priceAttribute());strategy.setValue(s.strategy());interval.getValueFactory().setValue(s.interval());validity.getValueFactory().setValue(s.validity());active.setSelected(s.active());});
        var save=button("Guardar fuente",()->{var s=sourceList.getSelectionModel().getSelectedItem();var data=new Store.Source(s==null?0:s.id(),required(name),required(url),title.getText().trim(),price.getText().trim(),titleAttr.getText().trim(),priceAttr.getText().trim(),strategy.getValue(),interval.getValue(),validity.getValue(),active.isSelected());work(()->{store.saveSource(data);return catalog();},this::apply);});
        var clear=button("Nueva",()->{sourceList.getSelectionModel().clearSelection();for(var f:List.of(name,url,title,price,titleAttr,priceAttr))f.clear();strategy.setValue("CSS");active.setSelected(true);});
        return column(new Label("Fuentes de precios públicos"),sourceList,form("Nombre",name,"URL base",url,"Estrategia",strategy,"Selector título",title,"Atributo título",titleAttr,"Selector precio",price,"Atributo precio",priceAttr,"Intervalo (min)",interval,"Vigencia del precio (h)",validity),flow(active,save,clear),
            new Label("CSS exige un título y un precio. VTEX lee skuJson público con una sola variante. JSON_LD requiere Product con una oferta PEN."));
    }
    private Node links(){
        var url=field("URL exacta de la ficha del producto");var condition=field("Condición: regular, oferta, tarjeta, ubicación...");var active=check("Activo",true);var schedule=check("Programar",false);
        linkList.setPrefHeight(250);linkList.getSelectionModel().selectedItemProperty().addListener((o,a,l)->{if(l==null)return;pickById(productPick,l.productId());pickSource(sourcePick,l.sourceId());url.setText(l.url());condition.setText(l.condition());active.setSelected(l.active());schedule.setSelected(l.scheduled());});
        var save=button("Guardar vínculo",()->{var p=selected(productPick);var s=selected(sourcePick);var l=linkList.getSelectionModel().getSelectedItem();String u=required(url),c=required(condition);boolean enabled=active.isSelected(),scheduled=schedule.isSelected();work(()->{store.saveLink(l==null?0:l.id(),p.id(),s.id(),u,c,scheduled,enabled);return catalog();},this::apply);});
        var clear=button("Nuevo",()->{linkList.getSelectionModel().clearSelection();url.clear();condition.clear();schedule.setSelected(false);active.setSelected(true);});
        return column(new Label("Asocia cada producto propio con una ficha en cada fuente"),linkList,form("Producto",productPick,"Fuente",sourcePick,"URL de ficha",url,"Condición de oferta",condition),flow(active,schedule,save,clear),new Label("La URL debe pertenecer al dominio de la fuente. No vincules páginas de búsqueda con varias ofertas."));
    }
    private Node analysis(){
        runLinks.setPrefHeight(145);candidates.setPrefHeight(180);var detail=new TextArea();detail.setEditable(false);detail.setPrefRowCount(6);var note=field("Describe las coincidencias o diferencias de marca, presentación, capacidad y oferta");
        runLinks.getSelectionModel().selectedItemProperty().addListener((o,a,l)->{if(l!=null)pickById(analysisProduct,l.productId());});
        analysisProduct.valueProperty().addListener((o,a,p)->{candidates.getItems().clear();detail.clear();});
        candidates.getSelectionModel().selectedItemProperty().addListener((o,a,c)->{if(c!=null)detail.setText("Título: "+c.title()+"\nPrecio: S/ "+c.price()+"\nCondición: "+c.condition()+"\nCapturado: "+c.date()+"\nURL: "+c.url()+"\nEvidencia: "+c.evidence());});
        var extract=button("Extraer ficha seleccionada",()->{var l=selected(runLinks);work(()->{new Scraper(store).run(l);return store.candidates(l.productId());},rows->{pickById(analysisProduct,l.productId());candidates.getItems().setAll(rows);status.setText("Extracción guardada. Verifica equivalencia antes de confirmar.");});});
        var preview=button("Probar extractor sin guardar precio",()->{var l=selected(runLinks);work(()->new Extractor().extract(store.source(l.sourceId()),l.url()),q->detail.setText("Vista previa · no persistida\n"+q.title()+"\nS/ "+q.price()+"\n"+q.url()+"\n"+q.evidence()));});
        var load=button("Consultar candidatos",()->{long p=selected(analysisProduct).id();work(()->store.candidates(p),rows->candidates.getItems().setAll(rows));});
        var confirm=button("Confirmar equivalencia",()->validate(true,note));var reject=button("Rechazar equivalencia",()->validate(false,note));
        var compare=button("Comparar y generar alerta",()->{long id=selected(candidates).id();work(()->{var r=store.compare(id);return new Object[]{r,catalog()};},data->{var r=(Economics.Result)data[0];apply((Catalog)data[1]);detail.setText("Diferencia externo − propio: S/ "+r.difference()+"\nUtilidad bruta unitaria actual: S/ "+r.currentProfit()+"\nUtilidad simulada: S/ "+r.simulatedProfit()+"\nMargen actual: "+r.currentMargin()+" %\nMargen simulado: "+r.simulatedMargin()+" %\n"+(r.loss()?"ALERTA: posible pérdida":r.lower()?"ALERTA: precio externo inferior":"Sin alerta de precio inferior"));});});
        return column(new Label("Selecciona una ficha configurada"),runLinks,flow(extract,preview,button("Actualizar catálogos",this::refresh)),flow(new Label("Producto para revisión"),analysisProduct,load),candidates,detail,note,flow(confirm,reject,compare),
            new Label("Confirma solo productos equivalentes. Rechaza 500 ml frente a 1 L. Los precios vencidos requieren nueva extracción."));
    }
    private void validate(boolean confirm,TextField note){long id=selected(candidates).id(),product=selected(analysisProduct).id();String evidence=required(note);work(()->{store.validate(id,confirm,evidence);return store.candidates(product);},rows->candidates.getItems().setAll(rows));}
    private Node alerts(){
        alerts.setPrefHeight(350);var state=new ComboBox<String>();state.getItems().setAll("EN_REVISION","ATENDIDA","DESCARTADA");state.setValue("EN_REVISION");var note=field("Observación y decisión comercial");
        var attend=button("Registrar atención",()->{long id=selected(alerts).id();String s=state.getValue(),n=required(note);work(()->{store.attend(id,s,n);return store.alerts();},v->alerts.getItems().setAll(v));});
        return column(new Label("Alertas · Cada atención conserva usuario, fecha y observación"),alerts,form("Nuevo estado",state,"Observación",note),flow(attend,button("Actualizar alertas",()->work(store::alerts,v->alerts.getItems().setAll(v)))));
    }
    private Node reports(){
        var type=new ComboBox<ReportType>();type.getItems().setAll(ReportType.values());type.setValue(ReportType.COMPARACIONES);var from=new DatePicker();var to=new DatePicker();var table=new TableView<List<String>>();table.setPrefHeight(430);var info=new Label("Selecciona un reporte. Fechas interpretadas en America/Lima; hasta incluye el día completo.");
        Supplier<ReportFilter> filter=()->new ReportFilter(reportProduct.getValue()==null?0:reportProduct.getValue().id(),reportSource.getValue()==null?0:reportSource.getValue().id(),from.getValue(),to.getValue());
        var consult=button("Consultar",()->{var f=filter.get();var t=type.getValue();if(t==ReportType.EXPORTACIONES)f=new ReportFilter(0,0,f.from(),f.to());var selectedFilter=f;work(()->store.report(t,selectedFilter),data->{showTable(table,data);info.setText(data.rows().size()+" registros · "+data.title());});});
        var clear=button("Todos los productos y fuentes",()->{reportProduct.setValue(null);reportSource.setValue(null);from.setValue(null);to.setValue(null);});
        return column(new Label("Reportes con filtros y procedencia"),form("Reporte",type,"Producto (vacío = todos)",reportProduct,"Fuente (vacío = todas)",reportSource,"Desde",from,"Hasta",to),flow(consult,clear,button("Exportar CSV",()->export(type.getValue(),filter.get(),false)),button("Exportar PDF",()->export(type.getValue(),filter.get(),true))),info,table);
    }
    private void export(ReportType type,ReportFilter filter,boolean pdf){
        var chooser=new FileChooser();chooser.setTitle("Guardar reporte");chooser.setInitialFileName(type.name().toLowerCase()+"-"+LocalDate.now(ZoneId.of("America/Lima"))+(pdf?".pdf":".csv"));chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(pdf?"PDF":"CSV",pdf?"*.pdf":"*.csv"));var file=chooser.showSaveDialog(stage);if(file==null)return;
        var f=type==ReportType.EXPORTACIONES?new ReportFilter(0,0,filter.from(),filter.to()):filter;
        work(()->{var data=store.report(type,f);Path path=file.toPath().toAbsolutePath();if(pdf)Reports.pdf(data,path);else Reports.csv(data,path);store.recordExport(type,f,path.toString());return path;},path->status.setText("Reporte guardado: "+path));
    }
    private Node users(){
        var list=new ListView<Store.User>();list.setPrefHeight(230);var email=field("Correo");var name=field("Nombre");var password=new PasswordField();password.setPromptText("Nueva contraseña; vacía al editar = conservar");var role=new ComboBox<String>();role.getItems().setAll("ADMIN","ANALISTA");role.setValue("ANALISTA");var active=check("Activo",true);
        list.getSelectionModel().selectedItemProperty().addListener((o,a,u)->{if(u!=null){email.setText(u.email());name.setText(u.name().replace(" [INACTIVO]",""));role.setValue(u.role());active.setSelected(!u.name().contains(" [INACTIVO]"));password.clear();}});
        var load=button("Actualizar usuarios",()->work(store::users,v->list.getItems().setAll(v)));
        var save=button("Guardar usuario",()->{var u=list.getSelectionModel().getSelectedItem();String e=required(email),n=required(name),p=password.getText(),r=role.getValue();boolean a=active.isSelected();work(()->{store.saveUser(u==null?0:u.id(),e,n,p,r,a);return store.users();},v->{password.clear();list.getItems().setAll(v);});});
        var clear=button("Nuevo",()->{list.getSelectionModel().clearSelection();email.clear();name.clear();password.clear();role.setValue("ANALISTA");active.setSelected(true);});
        return column(new Label("Usuarios y roles · Contraseñas con PBKDF2 y sal aleatoria"),list,form("Correo",email,"Nombre",name,"Contraseña",password,"Rol",role),flow(active,load,save,clear));
    }
    private Catalog catalog()throws Exception{return new Catalog(store.products(),store.sources(),store.links(),store.alerts());}
    private void refresh(){work(this::catalog,this::apply);}
    private void apply(Catalog c){
        long product=analysisProduct.getValue()==null?0:analysisProduct.getValue().id();
        productList.getItems().setAll(c.products());sourceList.getItems().setAll(c.sources());linkList.getItems().setAll(c.links());runLinks.getItems().setAll(c.links().stream().filter(Store.Link::active).toList());
        productPick.getItems().setAll(c.products());sourcePick.getItems().setAll(c.sources());analysisProduct.getItems().setAll(c.products());reportProduct.getItems().setAll(c.products());reportSource.getItems().setAll(c.sources());alerts.getItems().setAll(c.alerts());pickById(analysisProduct,product);
        summary.setText("Productos: "+c.products().size()+" · Fuentes: "+c.sources().size()+" · Fichas: "+c.links().size()+" · Alertas pendientes: "+c.alerts().stream().filter(a->a.status().equals("PENDIENTE")).count());
    }
    private void clearLists(){productList.getItems().clear();sourceList.getItems().clear();linkList.getItems().clear();runLinks.getItems().clear();candidates.getItems().clear();alerts.getItems().clear();}
    private void scheduled(){
        if(busy||store.session()==null||!scheduler.isSelected())return;
        work(()->{var errors=new ArrayList<String>();for(var link:store.dueLinks())try{new Scraper(store).run(link);}catch(Exception e){errors.add("#"+link.id()+": "+e.getMessage());}return errors;},errors->{if(!errors.isEmpty())status.setText("Monitoreo: "+String.join("; ",errors));else status.setText("Monitoreo revisado a las "+LocalTime.now(ZoneId.of("America/Lima")));});
    }
    private static void showTable(TableView<List<String>> table,ReportData data){table.getColumns().clear();for(int i=0;i<data.columns().size();i++){final int index=i;var column=new TableColumn<List<String>,String>(data.columns().get(i));column.setCellValueFactory(c->new SimpleStringProperty(c.getValue().get(index)));column.setPrefWidth(180);table.getColumns().add(column);}table.getItems().setAll(data.rows());}
    private static Tab tab(String label,Node node){var scroll=new ScrollPane(node);scroll.setFitToWidth(true);return new Tab(label,scroll);}
    private static VBox column(Node... nodes){var box=new VBox(12,nodes);box.setPadding(new Insets(18));return box;}
    private static FlowPane flow(Node... nodes){return new FlowPane(10,10,nodes);}
    private static GridPane form(Object... pairs){var grid=new GridPane();grid.setHgap(14);grid.setVgap(9);var label=new ColumnConstraints(190);var value=new ColumnConstraints();value.setHgrow(Priority.ALWAYS);grid.getColumnConstraints().addAll(label,value);for(int i=0;i<pairs.length;i+=2){grid.add(new Label(pairs[i].toString()),0,i/2);var node=(Node)pairs[i+1];grid.add(node,1,i/2);if(node instanceof Control control)control.setMaxWidth(Double.MAX_VALUE);}return grid;}
    private static TextField field(String prompt){var f=new TextField();f.setPromptText(prompt);f.setPrefWidth(500);return f;}
    private static CheckBox check(String label,boolean selected){var c=new CheckBox(label);c.setSelected(selected);return c;}
    private static String required(TextField f){return Store.required(f.getText(),f.getPromptText());}
    private static <T>T selected(ComboBox<T> box){if(box.getValue()==null)throw new IllegalArgumentException("Selecciona un elemento.");return box.getValue();}
    private static <T>T selected(ListView<T> list){var value=list.getSelectionModel().getSelectedItem();if(value==null)throw new IllegalArgumentException("Selecciona un registro.");return value;}
    private static void pickById(ComboBox<Store.Product> box,long id){box.setValue(box.getItems().stream().filter(p->p.id()==id).findFirst().orElse(null));}
    private static void pickSource(ComboBox<Store.Source> box,long id){box.setValue(box.getItems().stream().filter(s->s.id()==id).findFirst().orElse(null));}
    private Button button(String label,Runnable action){var b=new Button(label);b.setOnAction(e->{try{action.run();}catch(Exception ex){error(ex);}});return b;}
    private void error(Throwable e){status.setText("Operación fallida: "+e.getMessage());var alert=new Alert(Alert.AlertType.ERROR);alert.setHeaderText("No se pudo completar la operación");alert.setContentText(e.getMessage());alert.show();}
    private <T>void work(Callable<T> action,Consumer<T> success){
        if(busy)return;busy=true;root.setDisable(true);progress.setVisible(true);status.setText("Procesando…");
        var task=new Task<T>(){@Override protected T call()throws Exception{return action.call();}};
        task.setOnSucceeded(e->{busy=false;root.setDisable(false);progress.setVisible(false);status.setText("Operación completada");try{success.accept(task.getValue());}catch(Exception ex){error(ex);}});
        task.setOnFailed(e->{busy=false;root.setDisable(false);progress.setVisible(false);error(task.getException());if(root.getCenter()==null)connectionScreen();});worker.submit(task);
    }
    @Override public void stop(){ticker.stop();worker.shutdownNow();store.logout();}
    public static void main(String[] args){launch(args);}
}
