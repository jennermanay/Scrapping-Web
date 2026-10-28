package pe.edu.monitoreo;

import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Pos;
import java.util.*;

/** Dashboard charts use the current catalog snapshot; empty data never becomes a fake series. */
final class DashboardView extends VBox {
    private final Label products=new Label("0"),sources=new Label("0"),monitored=new Label("0"),pending=new Label("0");
    private final Label productHint=new Label(),sourceHint=new Label(),alertHint=new Label(),updated=new Label();
    private final PieChart alerts=new PieChart();
    private final BarChart<Number,String> coverage=new BarChart<>(new NumberAxis(),new CategoryAxis());
    private final Label noAlerts=new Label("Aún no hay alertas registradas."),noSources=new Label("Configura fuentes externas para ver su cobertura.");
    private final VBox sourceList=new VBox(10);
    private final Label alertTotal=new Label("0");
    private final VBox alertCenter=new VBox(0,alertTotal,new Label("alertas"));
    private final FlowPane alertLegend=new FlowPane(14,5);
    DashboardView(){
        getStyleClass().add("dashboard");setSpacing(16);setMinWidth(0);
        var title=label("Resumen del monitoreo","page-title");var subtitle=label("Productos, fuentes y alertas en un solo lugar.","muted");
        var metrics=new TilePane(16,16);metrics.setPrefColumns(4);metrics.setPrefTileWidth(250);metrics.setTileAlignment(Pos.TOP_LEFT);
        metrics.getChildren().addAll(metric("Productos registrados",products,productHint,"red"),metric("Fuentes configuradas",sources,sourceHint,"blue"),metric("Productos con monitoreo",monitored,new Label("Con fuentes externas activas"),"teal"),metric("Alertas pendientes",pending,alertHint,"amber"));
        metrics.prefTileWidthProperty().bind(javafx.beans.binding.Bindings.when(widthProperty().lessThan(1000)).then(widthProperty().subtract(72).divide(2)).otherwise(widthProperty().subtract(104).divide(4)));
        alerts.setId("dashboard-alerts-chart");alerts.setAnimated(false);alerts.setLabelsVisible(false);alerts.setLegendVisible(false);alerts.setStartAngle(90);alerts.setMinSize(0,180);alerts.setPrefHeight(190);
        coverage.setId("dashboard-coverage-chart");coverage.setAnimated(false);coverage.setLegendVisible(false);coverage.setCategoryGap(12);coverage.setMinSize(0,180);coverage.setPrefHeight(190);
        var x=(NumberAxis)coverage.getXAxis();x.setLabel("Productos incluidos");x.setTickUnit(1);x.setMinorTickVisible(false);x.setForceZeroInRange(true);x.setTickLabelFormatter(new javafx.util.StringConverter<Number>(){public String toString(Number n){return n.doubleValue()==Math.rint(n.doubleValue())?String.valueOf(n.intValue()):"";}public Number fromString(String s){return Integer.valueOf(s);}});
        alertCenter.setAlignment(Pos.CENTER);alertCenter.setMaxSize(82,82);alertCenter.setMinSize(82,82);alertCenter.getStyleClass().add("donut-center");alertCenter.setMouseTransparent(true);alertTotal.getStyleClass().add("donut-total");
        var coveragePlot=new StackPane(coverage,noSources);var alertPlot=new StackPane(alerts,alertCenter,noAlerts);
        var charts=new HBox(16);
        var coverageCard=panel("Productos por fuente",label("Fuentes externas activas seleccionadas por producto","muted"),coveragePlot);
        var alertCard=panel("Estado de las alertas",alertLegend,alertPlot);
        HBox.setHgrow(coverageCard,Priority.ALWAYS);HBox.setHgrow(alertCard,Priority.ALWAYS);coverageCard.prefWidthProperty().bind(widthProperty().subtract(64).divide(2));alertCard.prefWidthProperty().bind(widthProperty().subtract(64).divide(2));charts.getChildren().addAll(coverageCard,alertCard);
        updated.getStyleClass().add("muted");getChildren().addAll(title,metrics,charts,updated);
    }
    void update(DashboardModel model){
        products.setText(""+model.products().size());productHint.setText(model.products().stream().filter(Store.Product::active).count()+" activos en el catálogo");
        sources.setText(""+model.sources().size());sourceHint.setText(model.activeSources()+" activas · "+(model.sources().size()-model.activeSources())+" inactivas");
        var counts=model.monitoredCounts();monitored.setText(""+model.monitoredProducts());pending.setText(""+model.pendingAlerts());alertHint.setText(model.alerts().size()+" alertas en total");
        var series=new XYChart.Series<Number,String>();for(var source:model.externalSources())series.getData().add(new XYChart.Data<>(counts.getOrDefault(source.id(),0L),source.name()+" (#"+source.id()+")"));
        var axis=(NumberAxis)coverage.getXAxis();long max=counts.values().stream().mapToLong(Long::longValue).max().orElse(0);double step=Math.max(1,Math.ceil(max/5.0));axis.setAutoRanging(false);axis.setLowerBound(0);axis.setUpperBound(Math.max(1,Math.ceil(max/step)*step));axis.setTickUnit(step);
        coverage.getData().clear();if(!series.getData().isEmpty())coverage.getData().add(series);coverage.setPrefHeight(Math.max(190,series.getData().size()*28+40));showEmpty(coverage,noSources,series.getData().isEmpty());
        alertLegend.getChildren().clear();alertTotal.setText(""+model.alerts().size());alertCenter.setVisible(!model.alerts().isEmpty());alerts.getData().clear();for(var entry:model.alertCounts().entrySet())if(entry.getValue()>0){var data=new PieChart.Data(stateLabel(entry.getKey())+" ("+entry.getValue()+")",entry.getValue());data.nodeProperty().addListener((o,a,node)->{if(node!=null)node.setStyle("-fx-pie-color: "+stateColor(entry.getKey())+";");});alerts.getData().add(data);var dot=new Region();dot.setMinSize(8,8);dot.setPrefSize(8,8);dot.setMaxSize(8,8);dot.setStyle("-fx-background-radius: 4; -fx-background-color: "+stateColor(entry.getKey())+";");var item=new HBox(5,dot,label(stateLabel(entry.getKey())+": "+entry.getValue(),"muted"));item.setAlignment(Pos.CENTER_LEFT);alertLegend.getChildren().add(item);}
        if(alertLegend.getChildren().isEmpty())alertLegend.getChildren().add(label("Distribución por estado","muted"));
        showEmpty(alerts,noAlerts,alerts.getData().isEmpty());updated.setText("Actualizado: "+java.time.ZonedDateTime.now(java.time.ZoneId.of("America/Lima")).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

    }
    private static void showEmpty(Node chart,Node empty,boolean value){chart.setVisible(!value);empty.setVisible(value);}
    private static Label label(String text,String style){var label=new Label(text);label.getStyleClass().add(style);return label;}
    private static VBox metric(String title,Label value,Label hint,String accent){value.getStyleClass().add("metric-value");hint.getStyleClass().add("muted");var box=new VBox(7,label(title,"metric-title"),value,hint);box.getStyleClass().addAll("metric-card","metric-"+accent);box.setMinWidth(0);box.setMaxWidth(Double.MAX_VALUE);return box;}
    private static VBox panel(String title,Node... content){var box=new VBox(12);box.getStyleClass().add("panel");box.setMinWidth(0);box.getChildren().add(label(title,"section-title"));box.getChildren().addAll(content);return box;}
    private static String stateLabel(String state){return switch(state){case "PENDIENTE"->"Pendientes";case "EN_REVISION"->"En revisión";case "ATENDIDA"->"Atendidas";case "DESCARTADA"->"Descartadas";default->state;};}
    private static String stateColor(String state){return switch(state){case "PENDIENTE"->"#D12B35";case "EN_REVISION"->"#D59723";case "ATENDIDA"->"#218579";default->"#8593A5";};}
}
