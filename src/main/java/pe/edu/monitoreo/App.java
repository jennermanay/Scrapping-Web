package pe.edu.monitoreo;

import javafx.application.Application;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.concurrent.Task;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
    private final MonitoringController controller;
    @FunctionalInterface interface ProductLoader {PlazaVeaImporter.Preview load(String url)throws Exception;}
    private final ProductLoader productLoader;
    private final ISearchFuente searchLoader;
    public App(){this(new Store());}
    App(Store store){this(store,new PlazaVeaImporter()::inspect);}
    App(Store store,ProductLoader loader){this(store,loader,new SearchService());}
    App(Store store,ProductLoader loader,ISearchFuente search){this.store=store;productLoader=loader;searchLoader=search;controller=new MonitoringController(store,new Extractor(),search);}
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"monitoreo-worker");t.setDaemon(true);return t;});
    private final ExecutorService scrapingWorker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"scraping-background");t.setDaemon(true);return t;});
    private final javafx.beans.property.BooleanProperty scrapingBusy=new javafx.beans.property.SimpleBooleanProperty(false);
    private final Label scrapingMessage=new Label();
    private final Map<Long,ProductScraping.Result> completedScrapes=new HashMap<>();
    private Task<ProductScraping.Result> scrapingTask;
    private long scrapingGeneration;
    private final ListView<Store.User> userList=new ListView<>();
    private final ComboBox<PriceDecision.Action> priceAction=new ComboBox<>();
    private final TextField proposedPrice=new TextField();
    private final Label priceSimulation=new Label();
    private final Button savePriceDecision=new Button("Guardar decisión de precio");
    private Store.AlertDetail selectedAlertDetail;
    private boolean loadingPriceDecision;
    private final BorderPane root=new BorderPane();
    private final Label status=new Label("Preparando base de datos…");
    private final ProgressIndicator progress=new ProgressIndicator();
    private Set<Module> sessionPermissions=Set.of();
    private final Spinner<Integer> monitoringInterval=new Spinner<>(1,10080,60);
    private final CheckBox monitoringScheduled=new CheckBox("Consulta automática");
    private final ComboBox<String> userRoles=new ComboBox<>();
    private record SourceChoice(long id,String name){@Override public String toString(){return name;}}
    private final ComboBox<SourceChoice> extractionSource=new ComboBox<>();
    private List<Store.Candidate> extractionRows=List.of();
    private List<ProductScraping.SourceResult> extractionSourceRows=List.of();
    private final ListView<Store.Role> roleList=new ListView<>();
    private final TextArea alertDetail=new TextArea();
    private Set<Long> knownAlerts;
    private final Deque<Store.AlertDetail> notifications=new ArrayDeque<>();
    private Alert notification;

    private final ComboBox<Store.Product> productPick=new ComboBox<>();
    private final ComboBox<Store.Source> sourcePick=new ComboBox<>();
    private final ComboBox<Store.Product> analysisProduct=new ComboBox<>();
    private final ListView<Store.Product> productList=new ListView<>();
    private final ListView<Store.Source> sourceList=new ListView<>();
    private final ListView<Store.Link> linkList=new ListView<>();
    private final TableView<Store.Homologated> homologatedTable=new TableView<>();
    private final TableView<Store.Candidate> candidates=new TableView<>();
    private final ListView<Store.AlertItem> alerts=new ListView<>();
    private final ComboBox<Store.Product> reportProduct=new ComboBox<>();
    private final ComboBox<Store.Source> reportSource=new ComboBox<>();
    private final ComboBox<Store.Product> monitoringProduct=new ComboBox<>();
    private final VBox monitoringSources=new VBox(8);
    private final Map<Long,CheckBox> sourceChecks=new LinkedHashMap<>();
    private DashboardModel currentDashboardModel;
    private final TableView<ProductScraping.SourceResult> sourceRuns=new TableView<>();
    private DashboardView dashboard;
    private final TableView<Store.Product> monitoringProducts=new TableView<>();
    private TabPane mainTabs;
    private final Timeline ticker=new Timeline(new KeyFrame(Duration.seconds(15),e->scheduled()));
    private final Label summary=new Label();
    private boolean busy;
    private boolean applyingCatalog;
    private boolean pollingScheduler;
    private Stage stage;
    @Override public void start(Stage stage){
        this.stage=stage;progress.setPrefSize(22,22);progress.setVisible(false);
        status.setWrapText(true);status.setMinWidth(0);HBox.setHgrow(status,Priority.ALWAYS);
        var footer=new HBox(10,progress,status);footer.setAlignment(Pos.CENTER_LEFT);footer.getStyleClass().add("status-bar");root.setBottom(footer);
        stage.setTitle("Monitoreo competitivo de precios");stage.setScene(new Scene(root));stage.getScene().getStylesheets().add(Objects.requireNonNull(App.class.getResource("/styles/app.css")).toExternalForm());stage.setResizable(true);sizeWindow(false);stage.show();
        ticker.setCycleCount(Timeline.INDEFINITE);
        connectionScreen();
        work(()->{store.initialize();return store.needsSetup();},this::loginScreen);
    }
    private void connectionScreen(){
        sizeWindow(false);root.setTop(null);ticker.stop();
        root.setCenter(column(Branding.logo(140),new Label("Conexión"),button("Reintentar",()->work(()->{store.reloadConfiguration();store.initialize();return store.needsSetup();},this::loginScreen))));
    }
    private void loginScreen(boolean setup){
        sizeWindow(false);
        root.setTop(null);ticker.stop();
        var username=field("Usuario");var name=field("Nombre del administrador");var password=new PasswordField();password.setPromptText("Contraseña");
        var login=button(setup?"Crear administrador inicial":"Ingresar",()->{
            String e=required(username),p=password.getText(),n=name.getText();
            work(()->{if(setup)store.setup(e,n,p);store.login(e,p);var permissions=store.permissions();if(permissions.isEmpty()){store.logout();throw new SecurityException("El rol no tiene módulos habilitados.");}return permissions;},permissions->{sessionPermissions=permissions;password.clear();mainScreen();refresh();});
        });
        login.setDefaultButton(true);
        var title=new Label(setup?"Crea tu cuenta":"Bienvenido");title.getStyleClass().add("login-title");
        
        var box=new VBox(12,Branding.logo(148),title,new Label("Usuario"),username);
        if(setup)box.getChildren().addAll(new Label("Nombre"),name);
        box.getChildren().addAll(new Label("Contraseña"),password,login);
        box.getStyleClass().add("login-card");box.setMaxWidth(440);box.setMaxHeight(Region.USE_PREF_SIZE);login.setMaxWidth(Double.MAX_VALUE);
        var background=new StackPane(box);background.getStyleClass().add("login-background");background.setPadding(new Insets(18));root.setCenter(background);
        status.setText("");
    }
    private void mainScreen(){
        sizeWindow(true);
        var user=store.session();var tabs=new TabPane();tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        mainTabs=tabs;
        if(can(Module.INICIO))tabs.getTabs().add(tab("Inicio",home()));
        if(can(Module.PRODUCTOS)){tabs.getTabs().add(tab("Producto Plaza Vea",plazaProduct()));tabs.getTabs().add(tab("Productos",products()));}
        if(can(Module.MONITOREO))tabs.getTabs().add(tab("Configurar monitoreo",monitoring()));
        if(can(Module.FUENTES))tabs.getTabs().add(tab("Fuentes",sources()));
        if(can(Module.FICHAS)){var approvedTab=tab("Productos Homologados",links());approvedTab.setOnSelectionChanged(e->{if(approvedTab.isSelected())loadHomologated();});tabs.getTabs().add(approvedTab);}
        if(can(Module.EXTRACCION))tabs.getTabs().add(tab("Extracción y homologación",analysis()));
        if(can(Module.ALERTAS))tabs.getTabs().add(tab("Alertas",alerts()));
        if(can(Module.REPORTES))tabs.getTabs().add(tab("Historial y reportes",reports()));
        if(can(Module.USUARIOS)){var userTab=tab("Usuarios",users());userTab.setOnSelectionChanged(e->{if(userTab.isSelected())loadUsers();});tabs.getTabs().add(userTab);}
        if(can(Module.ROLES))tabs.getTabs().add(tab("Roles",roles()));
        var logout=button("Cerrar sesión",()->{ticker.stop();store.logout();clearLists();loginScreen(false);});
        var title=new Label("Monitoreo de precios");title.getStyleClass().add("app-title");
        var heading=new VBox(3,title);var spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        var account=new Label(user.toString());account.getStyleClass().add("account-label");logout.getStyleClass().add("quiet-button");
        var header=new HBox(24,Branding.logo(140),heading,spacer,account,logout);header.setAlignment(Pos.CENTER_LEFT);header.getStyleClass().add("app-header");root.setTop(header);root.setCenter(tabs);
        ticker.play();
    }
    private boolean can(Module module){return sessionPermissions.contains(module);}
    private void selectTab(String name){if(mainTabs!=null)mainTabs.getTabs().stream().filter(t->t.getText().equals(name)).findFirst().ifPresent(t->mainTabs.getSelectionModel().select(t));}
    private Node plazaProduct(){
        var url=field("URL del producto de Plaza Vea");
        var code=field("Código de Plaza Vea");var name=field("Nombre obtenido de Plaza Vea");var brand=field("Marca obtenida de la ficha");var model=field("Variedad / modelo");var presentation=field("Presentación obtenida de la ficha");var capacity=field("Capacidad y unidad");var category=field("Categoría");var keywords=field("Palabras clave separadas por coma");var ownPrice=field("Precio propio obtenido de Plaza Vea");ownPrice.setEditable(false);var cost=field("Costo unitario");var condition=field("Condición del precio");
        var evidence=new TextArea();evidence.setEditable(false);evidence.setPrefRowCount(5);var reviewed=check("Revisé los datos y las condiciones del producto de Plaza Vea",false);
        PlazaVeaImporter.Preview[] pending={null};
        var save=button("Guardar producto",()->{
            var preview=pending[0];if(preview==null)throw new IllegalArgumentException("Primero pulsa Obtener datos de Plaza Vea.");
            if(!reviewed.isSelected())throw new IllegalArgumentException("Revisa los datos, las advertencias y las condiciones antes de guardar.");
            if(!url.getText().trim().equals(preview.url()))throw new IllegalArgumentException("La URL cambió. Vuelve a obtener los datos antes de guardar.");
            String c=required(code),n=required(name),b=required(brand),m=model.getText().trim(),p=required(presentation),size=required(capacity),cat=required(category),keys=keywords.getText(),offer=required(condition);var unitCost=PriceParser.parse(required(cost));
            work(()->{long existing=store.products().stream().filter(x->x.code().equals(c)).mapToLong(Store.Product::id).findFirst().orElse(0);
                long id=store.importReference(preview,new Store.Product(existing,c,n,b,m,p,size,cat,keys,unitCost,preview.price(),true),offer);return new Object[]{catalog(),id};
            },data->{apply((DashboardModel)data[0]);pickById(monitoringProduct,(Long)data[1]);pending[0]=null;reviewed.setSelected(false);selectTab(can(Module.MONITOREO)?"Configurar monitoreo":"Productos");status.setText("Producto guardado.");});
        });
        var fetch=button("Obtener datos de Plaza Vea",()->{
            String address=required(url);PlazaVeaImporter.validateUrl(address);pending[0]=null;reviewed.setSelected(false);
            work(()->productLoader.load(address),preview->{pending[0]=preview;url.setText(preview.url());code.setText(preview.code());name.setText(preview.title());brand.setText(preview.brand());model.setText(preview.model());presentation.setText(preview.presentation());capacity.setText(preview.capacity());category.setText(preview.category());keywords.setText(preview.brand().toLowerCase(Locale.ROOT));ownPrice.setText(preview.price().toPlainString());cost.clear();condition.clear();
                evidence.setText("SKU: "+preview.sku()+" · EAN: "+preview.ean()+"\nCapturado: "+preview.captured()+"\n"+preview.evidence()+"\n"+String.join("\n",preview.warnings()));
                status.setText("Datos obtenidos.");});
        });
        return column(new Label("Producto Plaza Vea"),url,fetch,
            form("Código",code,"Nombre",name,"Marca",brand,"Variedad / modelo",model,"Presentación",presentation,"Capacidad",capacity,"Categoría",category,"Palabras clave",keywords,"Precio de venta (S/)",ownPrice,"Costo unitario (S/)",cost,"Condición del precio",condition),evidence,reviewed,save);
    }
    private Node monitoring(){
        monitoringProducts.setId("monitoring-products");monitoringProducts.getColumns().clear();
        for(String heading:List.of("Código","Producto","Marca","Presentación / capacidad","Plaza Vea (S/)")){
            var col=new TableColumn<Store.Product,String>(heading);col.setCellValueFactory(cell->{var p=cell.getValue();return new SimpleStringProperty(switch(heading){case "Código"->p.code();case "Producto"->p.name();case "Marca"->p.brand();case "Presentación / capacidad"->p.presentation()+" · "+p.capacity();default->p.price()==null?"":p.price().toPlainString();});});col.setPrefWidth(heading.equals("Producto")?420:150);monitoringProducts.getColumns().add(col);
        }
        monitoringProducts.setPrefHeight(200);monitoringProducts.setPlaceholder(new Label("Sin productos."));
        monitoringProducts.getSelectionModel().selectedItemProperty().addListener((o,a,p)->{if(p!=null)monitoringProduct.setValue(p);});
        monitoringProduct.valueProperty().addListener((o,a,p)->{if(p!=null)monitoringProducts.getSelectionModel().select(p);renderSourceChecks();});
        monitoringSources.setId("monitoring-sources");
        monitoringInterval.setId("monitoring-frequency");monitoringScheduled.setId("monitoring-enabled");
        var save=button("Guardar fuentes del producto",()->{long product=selected(monitoringProduct).id();var ids=new LinkedHashSet<Long>();sourceChecks.forEach((id,box)->{if(box.isSelected())ids.add(id);});boolean repeat=monitoringScheduled.isSelected();int minutes=monitoringInterval.getValue();
            work(()->{return controller.configure(product,ids,repeat,minutes);},data->{apply(data);status.setText("Configuración guardada · "+minutes+" min · "+ids.size()+" fuentes.");});});
        return column(new Label("Configurar monitoreo por producto"),monitoringProducts,form("Producto seleccionado",monitoringProduct),
            new Label("Fuentes"),monitoringSources,form("Frecuencia (min)",monitoringInterval),monitoringScheduled,
            flow(save,button("Actualizar productos y fuentes",this::refresh),button("Administrar fuentes",()->selectTab("Fuentes")),button("Ir a extracción",()->{var p=selected(monitoringProduct);pickById(analysisProduct,p.id());selectTab("Extracción y homologación");})));
    }
    private void renderSourceChecks(){
        sourceChecks.clear();monitoringSources.getChildren().clear();if(currentDashboardModel==null)return;
        var product=monitoringProduct.getValue();var settings=product==null?new Store.MonitoringSettings(60,false):currentDashboardModel.monitoring().getOrDefault(product.id(),new Store.MonitoringSettings(60,false));monitoringInterval.getValueFactory().setValue(settings.minutes());monitoringScheduled.setSelected(settings.scheduled());
        for(var source:currentDashboardModel.sources()){
            if(!source.active()||source.strategy().equals("DEMO")||currentDashboardModel.links().stream().anyMatch(l->l.sourceId()==source.id()&&l.reference()))continue;
            String host=java.net.URI.create(source.url()).getHost();if(Set.of("plazavea.com.pe","www.plazavea.com.pe").contains(Objects.toString(host,"")))continue;
            boolean included=product!=null&&currentDashboardModel.links().stream().anyMatch(l->l.productId()==product.id()&&l.sourceId()==source.id()&&l.active()&&!l.reference());
            var box=check(source.name()+" · "+source.url(),included);box.setId("include-source-"+source.id());box.setDisable(product==null);sourceChecks.put(source.id(),box);monitoringSources.getChildren().add(box);
        }
        if(sourceChecks.isEmpty())monitoringSources.getChildren().add(new Label("Sin fuentes activas."));
    }
    private static String searchText(Store.Product own,SearchService.Result result){
        var text=new StringBuilder("VISTA PREVIA SIN GUARDAR. Para registrar precios, comparación y alertas utiliza Buscar y guardar candidatos.\n").append(result.summary()).append("\n");
        for(var quote:result.quotes()){var calculation=Economics.compare(own.cost(),own.price(),quote.price());text.append("\n").append(quote.title()).append("\nPlaza Vea: S/ ").append(own.price()).append(" · Precio externo capturado: S/ ").append(quote.price()).append(" · Diferencia: S/ ").append(calculation.difference()).append("\n").append(calculation.loss()?"Posible pérdida al igualar":calculation.lower()?"Posible alerta por precio externo menor":"Precio externo igual o mayor").append(" · equivalencia pendiente\n").append(quote.url()).append("\n");}
        if(!result.incidents().isEmpty())text.append("\nIncidencias:\n").append(String.join("\n",result.incidents()));return text.toString();
    }
    private Node home(){dashboard=new DashboardView();return dashboard;}
    private Node products(){
        var code=field("Código único");var name=field("Nombre");var brand=field("Marca");var model=field("Modelo / variedad");var presentation=field("Presentación");var capacity=field("Capacidad con unidad");var category=field("Categoría");category.setText("General");var keywords=field("Palabras clave separadas por coma");var cost=field("Costo unitario PEN");var price=field("Precio propio PEN");var active=check("Activo",true);
        productList.setPrefHeight(230);productList.getSelectionModel().selectedItemProperty().addListener((o,a,p)->{if(p==null)return;code.setText(p.code());name.setText(p.name());brand.setText(p.brand());model.setText(p.model());presentation.setText(p.presentation());capacity.setText(p.capacity());category.setText(p.category());keywords.setText(p.keywords());cost.setText(p.cost().toPlainString());price.setText(p.price().toPlainString());active.setSelected(p.active());});
        var save=button("Guardar producto",()->{
            var p=productList.getSelectionModel().getSelectedItem();var data=new Store.Product(p==null?0:p.id(),required(code),required(name),required(brand),model.getText().trim(),required(presentation),required(capacity),required(category),keywords.getText(),PriceParser.parse(cost.getText()),PriceParser.parse(price.getText()),active.isSelected());
            work(()->{store.saveProduct(data);return catalog();},this::apply);
        });
        if(!store.session().admin())save.disableProperty().bind(productList.getSelectionModel().selectedItemProperty().isNotNull());
        var clear=button("Nuevo",()->{productList.getSelectionModel().clearSelection();for(var f:List.of(code,name,brand,model,presentation,capacity,keywords,cost,price))f.clear();category.setText("General");active.setSelected(true);});
        return column(new Label("Productos"),productList,
            form("Código",code,"Nombre",name,"Marca",brand,"Modelo / variedad",model,"Presentación",presentation,"Capacidad",capacity,"Categoría",category,"Palabras clave",keywords,"Costo unitario (S/)",cost,"Precio de venta (S/)",price),flow(active,save,clear));
    }
    private Node sources(){
        var name=field("Nombre");var url=field("URL base del dominio");var title=field("Selector CSS título");var price=field("Selector CSS precio");var titleAttr=field("Vacío = texto; ejemplo: content");var priceAttr=field("Vacío = texto; ejemplo: content");var strategy=new ComboBox<String>();strategy.getItems().setAll("AUTO","CSS","VTEX","JSON_LD","DEMO");strategy.setValue("AUTO");var interval=new Spinner<Integer>(15,1440,60,15);var validity=new Spinner<Integer>(1,168,24);var active=check("Activa",true);
        name.setId("source-name");url.setId("source-url");strategy.setId("source-strategy");active.setMinWidth(90);
        var advanced=new TitledPane("Configuración avanzada CSS",form("Selector título",title,"Atributo título",titleAttr,"Selector precio",price,"Atributo precio",priceAttr));advanced.setId("source-css-options");advanced.setExpanded(true);advanced.visibleProperty().bind(strategy.valueProperty().isEqualTo("CSS"));advanced.managedProperty().bind(advanced.visibleProperty());
        var help=new Label();help.setWrapText(true);help.getStyleClass().add("muted");help.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(()->switch(strategy.getValue()){
            case "CSS"->"CSS es manual: indica selectores que encuentren exactamente un título y un precio en la ficha.";
            case "VTEX"->"VTEX lee el formato skuJson. Para detectar otros formatos de tienda utiliza AUTO.";
            case "JSON_LD"->"JSON_LD lee un producto con una oferta inequívoca en soles, sin selectores.";
            case "DEMO"->"DEMO solo procesa el ejemplo ficticio; no consulta una web.";
            default->"AUTO detecta el formato de la tienda sin selectores. Úsalo para Tottus, Wong, Metro, Makro y la tienda local de pruebas.";
        },strategy.valueProperty()));
        sourceList.setPrefHeight(210);sourceList.getSelectionModel().selectedItemProperty().addListener((o,a,s)->{if(s==null)return;name.setText(s.name());url.setText(s.url());title.setText(s.titleSelector());price.setText(s.priceSelector());titleAttr.setText(s.titleAttribute());priceAttr.setText(s.priceAttribute());strategy.setValue(s.strategy());interval.getValueFactory().setValue(s.interval());validity.getValueFactory().setValue(s.validity());active.setSelected(s.active());});
        var save=button("Guardar fuente",()->{var s=sourceList.getSelectionModel().getSelectedItem();var data=new Store.Source(s==null?0:s.id(),required(name),required(url),title.getText().trim(),price.getText().trim(),titleAttr.getText().trim(),priceAttr.getText().trim(),strategy.getValue(),interval.getValue(),validity.getValue(),active.isSelected());work(()->{store.saveSource(data);return catalog();},this::apply);});
        var clear=button("Nueva",()->{sourceList.getSelectionModel().clearSelection();for(var f:List.of(name,url,title,price,titleAttr,priceAttr))f.clear();strategy.setValue("AUTO");active.setSelected(true);});
        return column(new Label("Fuentes de precios públicos"),sourceList,
            form("Nombre",name,"URL base",url,"Detección",strategy),advanced,form("Vigencia del precio (h)",validity),flow(active,save,clear));
    }
    private Node links(){
        homologatedTable.setId("homologated-products");homologatedTable.setPrefHeight(420);homologatedTable.getColumns().clear();homologatedTable.setPlaceholder(new Label("Sin productos homologados."));
        var columns=new LinkedHashMap<String,Function<Store.Homologated,String>>();columns.put("Producto Plaza Vea",Store.Homologated::product);columns.put("Fuente",Store.Homologated::source);columns.put("Producto homologado",Store.Homologated::title);columns.put("Último precio (S/)",h->h.price().toPlainString());columns.put("Estado",Store.Homologated::state);columns.put("Última consulta",h->h.checked().atZoneSameInstant(ZoneId.of("America/Lima")).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));columns.put("URL",Store.Homologated::url);columns.put("Detalle",Store.Homologated::reason);
        columns.forEach((title,value)->{var col=new TableColumn<Store.Homologated,String>(title);col.setCellValueFactory(cell->new SimpleStringProperty(value.apply(cell.getValue())));col.setPrefWidth(title.contains("Producto")?260:170);homologatedTable.getColumns().add(col);});
        var view=button("Ver en extracción",()->{var h=selected(homologatedTable);selectTab("Extracción y homologación");pickById(analysisProduct,h.productId());extractionSource.getItems().stream().filter(f->f.id()==h.sourceId()).findFirst().ifPresent(extractionSource::setValue);long product=h.productId();work(()->store.candidates(product),this::setExtractionRows);});
        return column(new Label("Productos Homologados"),homologatedTable,flow(button("Actualizar",this::loadHomologated),view));
    }
    private void loadHomologated(){work(store::homologated,rows->homologatedTable.getItems().setAll(rows));}
    private Node analysis(){
        candidates.setId("candidate-comparisons");candidates.setPrefHeight(190);var detail=new TextArea();detail.setEditable(false);detail.setPrefRowCount(6);var summary=new Label("");var note=field("Observación de homologación");
        candidates.getColumns().clear();candidateColumn("Fuente",160,Store.Candidate::source);candidateColumn("Producto encontrado",330,Store.Candidate::title);candidateColumn("Plaza Vea (S/)",120,c->candidateOwn(c)==null?"":candidateOwn(c).toPlainString());candidateColumn("Precio capturado (S/)",145,c->c.price().toPlainString());candidateColumn("Diferencia (S/)",120,c->candidateCalculation(c)==null?"":candidateCalculation(c).difference().toPlainString());candidateColumn("Homologación",130,Store.Candidate::status);candidateColumn("Comparación / alerta",255,this::candidateAlert);
        candidates.setPlaceholder(new Label("Sin resultados."));
        analysisProduct.setId("extraction-product");analysisProduct.valueProperty().addListener((o,a,p)->{if(!applyingCatalog&&(a==null||p==null||a.id()!=p.id())){extractionRows=List.of();extractionSourceRows=List.of();candidates.getItems().clear();sourceRuns.getItems().clear();detail.clear();note.clear();refreshExtractionSources(false);if(p!=null&&completedScrapes.containsKey(p.id())){var result=completedScrapes.get(p.id());extractionSourceRows=result.sources();setExtractionRows(result.candidates());}}});
        extractionSource.setId("extraction-source");extractionSource.valueProperty().addListener((o,a,value)->filterExtraction());
        candidates.getItems().addListener((javafx.collections.ListChangeListener<Store.Candidate>)change->{if(!candidates.getItems().isEmpty())candidates.getSelectionModel().selectFirst();});
        candidates.getSelectionModel().selectedItemProperty().addListener((o,a,c)->{
            if(c==null){summary.setText("");detail.clear();return;}
            var own=analysisProduct.getValue();var calculation=candidateCalculation(c);String assessment="";
            if(own!=null){var check=Equivalence.assess(own,c.title());assessment="\nCoincidencia preliminar: "+check.confidence()+" %\n"+String.join("\n",check.reasons());}
            summary.setText("Plaza Vea: S/ "+candidateOwn(c)+" · Externo capturado: S/ "+c.price()+(calculation==null?"":" · Diferencia: S/ "+calculation.difference()+" · Margen simulado: "+calculation.simulatedMargin()+" %")+"\n"+candidateAlert(c));
            detail.setText("Título: "+c.title()+"\nCondición: "+c.condition()+"\nCapturado: "+c.date()+"\nURL: "+c.url()+"\nEvidencia: "+c.evidence()+assessment+(c.comparison()==null?"":"\nComparación histórica guardada #"+c.comparison().id()+". Conserva sus valores originales."));
        });
        sourceRuns.setId("source-run-results");sourceRuns.setPrefHeight(140);sourceRuns.getColumns().clear();
        for(String heading:List.of("Fuente","Estado","Candidatos","Detalle")){
            var col=new TableColumn<ProductScraping.SourceResult,String>(heading);col.setCellValueFactory(cell->{var r=cell.getValue();return new SimpleStringProperty(switch(heading){case "Fuente"->r.source();case "Estado"->r.state();case "Candidatos"->String.valueOf(r.count());default->r.detail();});});col.setPrefWidth(heading.equals("Detalle")?620:160);sourceRuns.getColumns().add(col);
        }
        sourceRuns.setPlaceholder(new Label("Sin ejecuciones."));
        var extract=button("Iniciar búsqueda / extracción",()->{long product=selected(analysisProduct).id();long source=extractionSource.getValue()==null?0:extractionSource.getValue().id();note.clear();startScraping(product,source);});extract.disableProperty().bind(scrapingBusy);
        var indicator=new ProgressIndicator();indicator.setPrefSize(20,20);var banner=new HBox(10,indicator,scrapingMessage);banner.setAlignment(Pos.CENTER_LEFT);banner.getStyleClass().add("scraping-banner");banner.setId("scraping-in-progress");banner.visibleProperty().bind(scrapingBusy);banner.managedProperty().bind(scrapingBusy);
        var load=button("Consultar candidatos",()->{long product=selected(analysisProduct).id();work(()->store.candidates(product),this::setExtractionRows);});
        var confirm=button("Confirmar y comparar",()->confirmComparison(note));var reject=button("Rechazar equivalencia",()->rejectCandidate(note));
        var evidence=new TitledPane("Detalle del producto",detail);evidence.setExpanded(false);
        return column(form("Producto",analysisProduct,"Fuente",extractionSource),flow(extract,load,button("Actualizar catálogos",this::refresh)),banner,sourceRuns,
            candidates,summary,note,flow(confirm,reject),evidence);
    }
    private java.math.BigDecimal candidateOwn(Store.Candidate candidate){if(candidate.comparison()!=null)return candidate.comparison().own();var own=analysisProduct.getValue();return own==null?null:own.price();}
    private Economics.Result candidateCalculation(Store.Candidate candidate){if(candidate.comparison()!=null)return candidate.comparison().result();var own=analysisProduct.getValue();return own==null?null:Economics.compare(own.cost(),own.price(),candidate.price());}
    private String candidateAlert(Store.Candidate candidate){
        if(candidate.status().equals("RECHAZADO"))return "Rechazado";
        if(candidate.comparison()!=null){String tracked=candidate.comparison().alertType();if(tracked.equals("SUBIDA_PRECIO"))return "Precio subió";if(tracked.equals("BAJADA_PRECIO"))return "Precio bajó";if(tracked.equals("SIN_ALERTA"))return "Homologado · Sin cambio de precio";}
        var result=candidateCalculation(candidate);if(result==null)return "";
        String type=result.loss()?"POSIBLE PÉRDIDA":result.lower()?"PRECIO EXTERNO MENOR":"SIN ALERTA DE PRECIO INFERIOR";
        return candidate.comparison()==null?"Por confirmar: "+type:"Guardada: "+type;
    }
    private void candidateColumn(String title,double width,Function<Store.Candidate,String> value){var column=new TableColumn<Store.Candidate,String>(title);column.setCellValueFactory(cell->new SimpleStringProperty(value.apply(cell.getValue())));column.setPrefWidth(width);candidates.getColumns().add(column);}
    private void confirmComparison(TextField note){
        long id=selected(candidates).id(),product=selected(analysisProduct).id();String evidence=note.getText().trim();
        work(()->{var result=controller.confirmAndCompare(id,evidence);return new Object[]{result,catalog(),store.candidates(product)};},data->{apply((DashboardModel)data[1]);pickById(analysisProduct,product);@SuppressWarnings("unchecked") var rows=(List<Store.Candidate>)data[2];setExtractionRows(rows);completedScrapes.computeIfPresent(product,(key,old)->new ProductScraping.Result(old.sources(),rows));rows.stream().filter(c->c.id()==id).findFirst().ifPresent(c->candidates.getSelectionModel().select(c));var result=(Economics.Result)data[0];status.setText("Homologación guardada.");});
    }
    private void rejectCandidate(TextField note){long id=selected(candidates).id(),product=selected(analysisProduct).id();String evidence=required(note);work(()->{store.validate(id,false,evidence);return store.candidates(product);},rows->{setExtractionRows(rows);completedScrapes.computeIfPresent(product,(key,old)->new ProductScraping.Result(old.sources(),rows));rows.stream().filter(c->c.id()==id).findFirst().ifPresent(c->candidates.getSelectionModel().select(c));});}
    private Node alerts(){
        alerts.setPrefHeight(230);alertDetail.setEditable(false);alertDetail.setPrefRowCount(7);alertDetail.setId("alert-detail");
        alerts.getSelectionModel().selectedItemProperty().addListener((o,a,item)->{if(item!=null)work(()->store.alertDetail(item.id()),this::displayAlertDetail);});
        priceAction.getItems().setAll(PriceDecision.Action.values());priceAction.setValue(PriceDecision.Action.KEEP);priceAction.setId("alert-price-action");proposedPrice.setPromptText("Nuevo precio de venta");proposedPrice.setId("alert-proposed-price");priceSimulation.setId("alert-price-simulation");priceSimulation.setWrapText(true);
        priceAction.valueProperty().addListener((o,a,value)->updatePriceSimulation(true));proposedPrice.textProperty().addListener((o,a,value)->updatePriceSimulation(false));
        var state=new ComboBox<String>();state.getItems().setAll("EN_REVISION","ATENDIDA","DESCARTADA");state.setValue("EN_REVISION");var note=field("Observación");
        var attend=button("Registrar atención",()->{long id=selected(alerts).id();String value=state.getValue(),text=required(note);work(()->{store.attend(id,value,text);return new Object[]{catalog(),store.alertDetail(id)};},data->{apply((DashboardModel)data[0]);displayAlertDetail((Store.AlertDetail)data[1]);status.setText("Atención registrada.");});});
        savePriceDecision.setId("save-alert-price");savePriceDecision.getStyleClass().add("primary-button");savePriceDecision.setDisable(true);savePriceDecision.setOnAction(event->{try{var detail=selectedAlertDetail;if(detail==null)throw new IllegalArgumentException("Selecciona una alerta.");var action=priceAction.getValue();var target=action==PriceDecision.Action.LOWER?PriceParser.parse(proposedPrice.getText()):null;String observation=note.getText();work(()->{store.decidePrice(detail.id(),action,target,detail.currentPrice(),detail.currentCost(),observation);return new Object[]{catalog(),store.alertDetail(detail.id())};},data->{apply((DashboardModel)data[0]);displayAlertDetail((Store.AlertDetail)data[1]);status.setText("Decisión de precio guardada.");});}catch(Exception error){error(error);}});
        return column(new Label("Alertas"),alerts,alertDetail,form("Decisión de precio",priceAction,"Nuevo precio de venta (S/)",proposedPrice),priceSimulation,form("Observación",note),flow(savePriceDecision),form("Estado",state),flow(attend,button("Actualizar alertas",this::refresh)));
    }
    private Node reports(){
        var type=new ComboBox<ReportType>();type.getItems().setAll(ReportType.values());type.setValue(ReportType.COMPARACIONES);var from=new DatePicker();var to=new DatePicker();var table=new TableView<List<String>>();table.setPrefHeight(430);var info=new Label("");
        Supplier<ReportFilter> filter=()->new ReportFilter(reportProduct.getValue()==null?0:reportProduct.getValue().id(),reportSource.getValue()==null?0:reportSource.getValue().id(),from.getValue(),to.getValue());
        var consult=button("Consultar",()->{var f=filter.get();var t=type.getValue();if(t==ReportType.EXPORTACIONES)f=new ReportFilter(0,0,f.from(),f.to());var selectedFilter=f;work(()->store.report(t,selectedFilter),data->{showTable(table,data);info.setText(data.rows().size()+" registros · "+data.title());});});
        var clear=button("Todos los productos y fuentes",()->{reportProduct.setValue(null);reportSource.setValue(null);from.setValue(null);to.setValue(null);});
        return column(new Label("Reportes con filtros y procedencia"),form("Reporte",type,"Producto (vacío = todos)",reportProduct,"Fuente (vacío = todas)",reportSource,"Desde",from,"Hasta",to),flow(consult,clear,button("Exportar CSV",()->export(type.getValue(),filter.get(),"csv")),button("Exportar PDF",()->export(type.getValue(),filter.get(),"pdf")),button("Exportar Excel",()->export(type.getValue(),filter.get(),"xlsx"))),info,table);
    }
    private void export(ReportType type,ReportFilter filter,String format){
        var chooser=new FileChooser();chooser.setTitle("Guardar reporte");chooser.setInitialFileName(type.name().toLowerCase()+"-"+LocalDate.now(ZoneId.of("America/Lima"))+"."+format);chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(format.toUpperCase(),"*."+format));var file=chooser.showSaveDialog(stage);if(file==null)return;
        var f=type==ReportType.EXPORTACIONES?new ReportFilter(0,0,filter.from(),filter.to()):filter;
        work(()->{var data=store.report(type,f);Path path=file.toPath().toAbsolutePath();switch(format){case "pdf"->Reports.pdf(data,path);case "xlsx"->Reports.xlsx(data,path);default->Reports.csv(data,path);}store.recordExport(type,f,path.toString());return path;},path->status.setText("Reporte guardado: "+path));
    }
    private Node users(){
        var list=userList;list.setId("users-list");list.setPrefHeight(230);var username=field("Usuario");var name=field("Nombre");var password=new PasswordField();password.setPromptText("Contraseña");var role=userRoles;role.setId("user-role");var active=check("Activo",true);
        list.getSelectionModel().selectedItemProperty().addListener((o,a,u)->{if(u!=null){username.setText(u.username());name.setText(u.name().replace(" [INACTIVO]",""));role.setValue(u.role());active.setSelected(!u.name().contains(" [INACTIVO]"));password.clear();}});
        var load=button("Actualizar usuarios",this::loadUsers);
        var save=button("Guardar usuario",()->{var u=list.getSelectionModel().getSelectedItem();String e=required(username),n=required(name),p=password.getText(),r=role.getValue();boolean a=active.isSelected();work(()->{store.saveUser(u==null?0:u.id(),e,n,p,r,a);return store.users();},v->{password.clear();list.getItems().setAll(v);});});
        var clear=button("Nuevo",()->{list.getSelectionModel().clearSelection();username.clear();name.clear();password.clear();role.setValue("ANALISTA");active.setSelected(true);});
        return column(new Label("Usuarios"),list,form("Usuario",username,"Nombre",name,"Contraseña",password,"Rol",role),flow(active,load,save,clear));
    }
    private Node roles(){
        roleList.setPrefHeight(180);roleList.setId("roles-list");var code=field("Código");code.setId("role-code");var name=field("Nombre");name.setId("role-name");var active=check("Activo",true);var checks=new EnumMap<Module,CheckBox>(Module.class);var modules=new FlowPane(20,12);
        for(var module:Module.values()){var box=check(module.toString(),false);box.setId("role-module-"+module.name());checks.put(module,box);modules.getChildren().add(box);}
        roleList.getSelectionModel().selectedItemProperty().addListener((o,a,role)->{if(role==null)return;code.setText(role.code());code.setEditable(false);name.setText(role.name());active.setSelected(role.active());boolean system=role.code().equals("ADMIN");active.setDisable(system);checks.forEach((module,box)->{box.setSelected(role.modules().contains(module));box.setDisable(system);});});
        var save=button("Guardar rol",()->{var selected=EnumSet.noneOf(Module.class);checks.forEach((module,box)->{if(box.isSelected())selected.add(module);});String role=required(code),title=required(name);boolean enabled=active.isSelected();work(()->{store.saveRole(role,title,enabled,selected);return catalog();},data->{apply(data);status.setText("Rol guardado.");});});
        var create=button("Nuevo rol",()->{roleList.getSelectionModel().clearSelection();code.clear();code.setEditable(true);name.clear();active.setDisable(false);active.setSelected(true);checks.values().forEach(box->{box.setDisable(false);box.setSelected(false);});});
        return column(new Label("Roles"),roleList,form("Código",code,"Nombre",name),new Label("Módulos"),modules,flow(active,save,create));
    }
    private void refreshExtractionSources(boolean preserve){
        if(currentDashboardModel==null)return;var product=analysisProduct.getValue();long selected=preserve&&extractionSource.getValue()!=null?extractionSource.getValue().id():0;
        var options=new ArrayList<SourceChoice>();options.add(new SourceChoice(0,"Todas las fuentes"));
        if(product!=null)for(var source:currentDashboardModel.sources())if(source.active()&&currentDashboardModel.links().stream().anyMatch(l->l.productId()==product.id()&&l.sourceId()==source.id()&&l.active()&&!l.reference()))options.add(new SourceChoice(source.id(),source.name()));
        extractionSource.getItems().setAll(options);long sourceId=selected;extractionSource.setValue(options.stream().filter(o->o.id()==sourceId).findFirst().orElse(options.getFirst()));
    }
    private void setExtractionRows(List<Store.Candidate> rows){extractionRows=List.copyOf(rows);filterExtraction();}
    private void filterExtraction(){long source=extractionSource.getValue()==null?0:extractionSource.getValue().id();candidates.getItems().setAll(extractionRows.stream().filter(c->source==0||c.sourceId()==source).toList());sourceRuns.getItems().setAll(extractionSourceRows.stream().filter(r->source==0||r.sourceId()==source).toList());if(!extractionRows.isEmpty()||!extractionSourceRows.isEmpty())status.setText("Fuentes: "+sourceRuns.getItems().size()+" · Resultados: "+candidates.getItems().size());}
    private void displayAlertDetail(Store.AlertDetail detail){selectedAlertDetail=detail;alertDetail.setText(detail.product()+"\nFuente: "+detail.source()+"\nPrecio de venta al comparar: S/ "+detail.own()+"\nPrecio competencia: S/ "+detail.external()+"\nDiferencia: S/ "+detail.difference()+"\nCosto unitario actual: S/ "+detail.currentCost()+" · Precio de venta actual: S/ "+detail.currentPrice()+(detail.previousPrice()==null?"":"\nPrecio anterior de la fuente: S/ "+detail.previousPrice()+" · Variación: S/ "+detail.priceChange())+"\nTipo: "+detail.type()+" · Estado: "+detail.status()+"\nFecha: "+detail.date().atZoneSameInstant(ZoneId.of("America/Lima")).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))+"\nURL: "+detail.url());
        loadingPriceDecision=true;priceAction.setValue(detail.decision()==null?PriceDecision.Action.KEEP:PriceDecision.Action.valueOf(detail.decision().action()));proposedPrice.setText((detail.decision()==null?detail.currentPrice():detail.decision().after()).toPlainString());loadingPriceDecision=false;updatePriceSimulation(true);
    }
    private void updatePriceSimulation(boolean resetPrice){
        if(loadingPriceDecision||selectedAlertDetail==null)return;var detail=selectedAlertDetail;var action=priceAction.getValue();loadingPriceDecision=true;
        try{boolean saved=detail.decision()!=null;priceAction.setDisable(saved);proposedPrice.setEditable(!saved&&action==PriceDecision.Action.LOWER);savePriceDecision.setDisable(saved);
            if(saved){var decision=detail.decision();priceSimulation.setText("Decisión guardada · Precio S/ "+decision.after()+" · Utilidad S/ "+decision.profit()+" · Margen "+decision.margin()+" %");return;}
            if(resetPrice)proposedPrice.setText(switch(action){case KEEP->detail.currentPrice().toPlainString();case MATCH->detail.external().toPlainString();case LOWER->detail.external().subtract(new java.math.BigDecimal("0.01")).max(new java.math.BigDecimal("0.01")).toPlainString();});
            var proposed=action==PriceDecision.Action.LOWER?PriceParser.parse(proposedPrice.getText()):null;var result=PriceDecision.simulate(action,detail.currentCost(),detail.currentPrice(),detail.external(),proposed);
            priceSimulation.setText("Utilidad actual: S/ "+detail.currentPrice().subtract(detail.currentCost())+" · Nueva utilidad: S/ "+result.profit()+" · Nuevo margen: "+result.margin()+" %"+(result.loss()?" · PÉRDIDA":""));
        }catch(Exception e){savePriceDecision.setDisable(true);priceSimulation.setText(e.getMessage());}finally{loadingPriceDecision=false;}
    }
    private void notifyAlerts(List<Store.AlertItem> items){
        var ids=items.stream().map(Store.AlertItem::id).collect(java.util.stream.Collectors.toSet());if(knownAlerts==null){knownAlerts=ids;return;}
        var fresh=items.stream().filter(a->!knownAlerts.contains(a.id())).toList();knownAlerts=ids;if(fresh.isEmpty()||!can(Module.ALERTAS))return;
        long actor=store.session().id();var task=new Task<List<Store.AlertDetail>>(){@Override protected List<Store.AlertDetail> call()throws Exception{var result=new ArrayList<Store.AlertDetail>();for(var item:fresh)result.add(store.alertDetail(item.id()));return result;}};
        task.setOnSucceeded(event->{if(store.session()==null||store.session().id()!=actor)return;notifications.addAll(task.getValue());showNextNotification();});
        task.setOnFailed(event->{if(store.session()!=null&&store.session().id()==actor&&knownAlerts!=null)fresh.forEach(item->knownAlerts.remove(item.id()));});worker.submit(task);
    }
    private void showNextNotification(){
        if(notification!=null||notifications.isEmpty()||store.session()==null)return;var detail=notifications.removeFirst();var view=new ButtonType("Ver alerta",ButtonBar.ButtonData.OK_DONE);
        notification=new Alert(Alert.AlertType.WARNING,detail.product()+"\n"+detail.source()+"\nPlaza Vea: S/ "+detail.own()+" · Externo: S/ "+detail.external()+"\nDiferencia con Plaza Vea: S/ "+detail.difference()+(detail.previousPrice()==null?"":"\nAnterior: S/ "+detail.previousPrice()+" · Actual: S/ "+detail.external()+" · Variación: S/ "+detail.priceChange()),view,ButtonType.CLOSE);
        notification.initOwner(stage);notification.initModality(Modality.NONE);notification.setTitle("Nueva alerta");notification.setHeaderText(switch(detail.type()){case "SUBIDA_PRECIO"->"El precio subió";case "BAJADA_PRECIO"->"El precio bajó";case "PERDIDA"->"Posible pérdida";default->"Precio inferior";});notification.getDialogPane().setId("price-alert-popup");notification.getDialogPane().getStylesheets().add(App.class.getResource("/styles/app.css").toExternalForm());
        var popup=notification;notification.setOnHidden(event->{var result=popup.getResult();if(notification==popup)notification=null;if(view.equals(result)){selectTab("Alertas");alerts.getItems().stream().filter(a->a.id()==detail.id()).findFirst().ifPresent(a->alerts.getSelectionModel().select(a));displayAlertDetail(detail);}showNextNotification();});notification.show();
    }
    private DashboardModel catalog()throws Exception{return controller.dashboard();}
    private void refresh(){work(this::catalog,this::apply);}
    private void apply(DashboardModel c){
        if(!sessionPermissions.equals(c.permissions())){ticker.stop();store.logout();clearLists();loginScreen(false);status.setText("Permisos actualizados.");return;}
        applyingCatalog=true;
        currentDashboardModel=c;roleList.getItems().setAll(c.roles());
        userRoles.getItems().setAll(c.roles().stream().filter(Store.Role::active).map(Store.Role::code).toList());if(userRoles.getValue()==null)userRoles.setValue("ANALISTA");

        long product=analysisProduct.getValue()==null?0:analysisProduct.getValue().id();
        productList.getItems().setAll(c.products());sourceList.getItems().setAll(c.sources());linkList.getItems().setAll(c.links());
        long ownSelection=monitoringProduct.getValue()==null?0:monitoringProduct.getValue().id();monitoringProduct.getItems().setAll(c.products().stream().filter(p->p.active()&&c.links().stream().anyMatch(l->l.productId()==p.id()&&l.reference())).toList());pickById(monitoringProduct,ownSelection);if(monitoringProduct.getValue()==null&&monitoringProduct.getItems().size()==1)monitoringProduct.getSelectionModel().selectFirst();
        monitoringProducts.getItems().setAll(monitoringProduct.getItems());if(monitoringProduct.getValue()!=null)monitoringProducts.getSelectionModel().select(monitoringProduct.getValue());
        renderSourceChecks();
        productPick.getItems().setAll(c.products());sourcePick.getItems().setAll(c.sources());analysisProduct.getItems().setAll(c.products());reportProduct.getItems().setAll(c.products());reportSource.getItems().setAll(c.sources());alerts.getItems().setAll(c.alerts());pickById(analysisProduct,product);
        summary.setText(c.summary());
        applyingCatalog=false;refreshExtractionSources(true);
        if(dashboard!=null)dashboard.update(c);
        notifyAlerts(c.alerts());
    }
    private void clearLists(){scrapingGeneration++;if(scrapingTask!=null)scrapingTask.cancel(true);scrapingTask=null;scrapingBusy.set(false);completedScrapes.clear();homologatedTable.getItems().clear();selectedAlertDetail=null;userList.getItems().clear();knownAlerts=null;notifications.clear();if(notification!=null){var popup=notification;notification=null;popup.setResult(ButtonType.CLOSE);popup.close();}currentDashboardModel=null;sourceChecks.clear();monitoringSources.getChildren().clear();sourceRuns.getItems().clear();productList.getItems().clear();sourceList.getItems().clear();linkList.getItems().clear();candidates.getItems().clear();alerts.getItems().clear();}
    private void scheduled(){
        if(busy||scrapingBusy.get()||pollingScheduler||store.session()==null||!can(Module.EXTRACCION))return;pollingScheduler=true;
        var poll=new Task<List<Store.Link>>(){@Override protected List<Store.Link> call()throws Exception{return store.dueLinks();}};
        poll.setOnSucceeded(event->{pollingScheduler=false;var due=poll.getValue();if(due.isEmpty()||busy||scrapingBusy.get()||store.session()==null)return;startScraping(due.getFirst().productId(),0,true);
        });poll.setOnFailed(event->{pollingScheduler=false;});worker.submit(poll);
    }
    private void loadUsers(){work(store::users,users->{userList.getItems().setAll(users);if(userList.getSelectionModel().isEmpty()&&!users.isEmpty())userList.getSelectionModel().selectFirst();});}
    private void startScraping(long product,long source){startScraping(product,source,false);}
    private void startScraping(long product,long source,boolean scheduled){
        if(scrapingBusy.get()||store.session()==null)return;long generation=++scrapingGeneration;long actor=store.session().id();
        String name=currentDashboardModel==null?"":currentDashboardModel.products().stream().filter(p->p.id()==product).map(Store.Product::name).findFirst().orElse("");scrapingMessage.setText("Scraping en proceso · "+name);scrapingBusy.set(true);
        if(analysisProduct.getValue()!=null&&analysisProduct.getValue().id()==product){extractionRows=List.of();extractionSourceRows=List.of();filterExtraction();}
        var task=new Task<ProductScraping.Result>(){@Override protected ProductScraping.Result call()throws Exception{return scheduled?controller.extractScheduled(product):controller.extract(product,source);}};scrapingTask=task;
        task.setOnSucceeded(event->{if(generation!=scrapingGeneration||store.session()==null||store.session().id()!=actor)return;scrapingBusy.set(false);scrapingTask=null;var result=task.getValue();completedScrapes.put(product,result);
            if(analysisProduct.getValue()!=null&&analysisProduct.getValue().id()==product){extractionSourceRows=result.sources();setExtractionRows(result.candidates());}status.setText("Scraping completado · "+result.candidates().size()+" resultados.");refreshAfterScraping();
        });
        task.setOnFailed(event->{if(generation!=scrapingGeneration)return;scrapingBusy.set(false);scrapingTask=null;status.setText("Scraping: "+DatabaseErrors.explain(task.getException()));});
        task.setOnCancelled(event->{if(generation==scrapingGeneration){scrapingBusy.set(false);scrapingTask=null;}});scrapingWorker.submit(task);
    }
    private void refreshAfterScraping(){
        if(store.session()==null)return;long actor=store.session().id();var task=new Task<DashboardModel>(){@Override protected DashboardModel call()throws Exception{return catalog();}};
        task.setOnSucceeded(event->{if(store.session()!=null&&store.session().id()==actor)apply(task.getValue());});task.setOnFailed(event->{if(store.session()!=null&&store.session().id()==actor)status.setText(DatabaseErrors.explain(task.getException()));});worker.submit(task);
    }
    private static void showTable(TableView<List<String>> table,ReportData data){table.getColumns().clear();for(int i=0;i<data.columns().size();i++){final int index=i;var column=new TableColumn<List<String>,String>(data.columns().get(i));column.setCellValueFactory(c->new SimpleStringProperty(c.getValue().get(index)));column.setPrefWidth(180);table.getColumns().add(column);}table.getItems().setAll(data.rows());}
    private void sizeWindow(boolean workspace){
        if(stage==null)return;
        var screens=Screen.getScreensForRectangle(stage.getX(),stage.getY(),Math.max(stage.getWidth(),1),Math.max(stage.getHeight(),1));
        var bounds=(screens.isEmpty()?Screen.getPrimary():screens.getFirst()).getVisualBounds();
        double width=Math.min(workspace?1380:720,bounds.getWidth()*0.94),height=Math.min(workspace?880:560,bounds.getHeight()*0.92);
        stage.setMinWidth(Math.min(workspace?900:540,width));stage.setMinHeight(Math.min(workspace?600:420,height));
        if(!stage.isMaximized()){stage.setWidth(width);stage.setHeight(height);stage.setX(bounds.getMinX()+(bounds.getWidth()-width)/2);stage.setY(bounds.getMinY()+(bounds.getHeight()-height)/2);}
    }
    private static Tab tab(String label,Node node){if(node instanceof VBox box&&!(node instanceof DashboardView)){box.getStyleClass().add("page");if(!box.getChildren().isEmpty()&&box.getChildren().getFirst() instanceof Label heading)heading.getStyleClass().add("page-title");}var scroll=new ScrollPane(node);scroll.getStyleClass().add("page-scroll");scroll.setFitToWidth(true);scroll.setPannable(true);return new Tab(label,scroll);}
    private static VBox column(Node... nodes){var box=new VBox(12,nodes);box.setPadding(new Insets(18));box.setMinWidth(0);for(var node:nodes)if(node instanceof Label label){label.setWrapText(true);label.setMinWidth(0);label.setMaxWidth(Double.MAX_VALUE);}return box;}
    private static FlowPane flow(Node... nodes){return new FlowPane(10,10,nodes);}
    private static GridPane form(Object... pairs){var grid=new GridPane();grid.setHgap(14);grid.setVgap(9);grid.setMinWidth(0);var label=new ColumnConstraints(140,170,190);var value=new ColumnConstraints(0,500,Double.MAX_VALUE);value.setHgrow(Priority.ALWAYS);grid.getColumnConstraints().addAll(label,value);for(int i=0;i<pairs.length;i+=2){var title=new Label(pairs[i].toString());title.setWrapText(true);grid.add(title,0,i/2);var node=(Node)pairs[i+1];grid.add(node,1,i/2);if(node instanceof Control control){control.setMinWidth(0);control.setMaxWidth(Double.MAX_VALUE);}}return grid;}
    private static TextField field(String prompt){var f=new TextField();f.setPromptText(prompt);f.setMinWidth(0);f.setPrefWidth(500);return f;}
    private static CheckBox check(String label,boolean selected){var c=new CheckBox(label);c.setSelected(selected);return c;}
    private static String required(TextField f){return Store.required(f.getText(),f.getPromptText());}
    private static <T>T selected(ComboBox<T> box){if(box.getValue()==null)throw new IllegalArgumentException("Selecciona un elemento.");return box.getValue();}
    private static <T>T selected(TableView<T> table){var value=table.getSelectionModel().getSelectedItem();if(value==null)throw new IllegalArgumentException("Selecciona un candidato.");return value;}
    private static <T>T selected(ListView<T> list){var value=list.getSelectionModel().getSelectedItem();if(value==null)throw new IllegalArgumentException("Selecciona un registro.");return value;}
    private static void pickById(ComboBox<Store.Product> box,long id){box.setValue(box.getItems().stream().filter(p->p.id()==id).findFirst().orElse(null));}
    private static void pickSource(ComboBox<Store.Source> box,long id){box.setValue(box.getItems().stream().filter(s->s.id()==id).findFirst().orElse(null));}
    private Button button(String label,Runnable action){var b=new Button(label);if(label.matches("^(Guardar|Iniciar|Obtener|Confirmar|Ingresar|Crear|Importar).*"))b.getStyleClass().add("primary-button");if(label.startsWith("Rechazar"))b.getStyleClass().add("danger-button");b.setOnAction(e->{try{action.run();}catch(Exception ex){error(ex);}});return b;}
    private void error(Throwable e){String message=DatabaseErrors.explain(e);status.setText("Operación fallida: "+message.replace('\n',' '));var alert=new Alert(Alert.AlertType.ERROR);alert.setHeaderText("No se pudo completar la operación");alert.setContentText(message);alert.show();}
    private <T>void work(Callable<T> action,Consumer<T> success){
        if(busy)return;busy=true;root.setDisable(true);progress.setVisible(true);status.setText("Procesando…");
        var task=new Task<T>(){@Override protected T call()throws Exception{return action.call();}};
        task.setOnSucceeded(e->{busy=false;root.setDisable(false);progress.setVisible(false);status.setText("Operación completada");try{success.accept(task.getValue());}catch(Exception ex){error(ex);}});
        task.setOnFailed(e->{busy=false;root.setDisable(false);progress.setVisible(false);error(task.getException());if(root.getCenter()==null)connectionScreen();});worker.submit(task);
    }
    @Override public void stop(){ticker.stop();scrapingGeneration++;if(scrapingTask!=null)scrapingTask.cancel(true);scrapingWorker.shutdownNow();worker.shutdownNow();store.logout();}
    public static void main(String[] args){launch(args);}
}
