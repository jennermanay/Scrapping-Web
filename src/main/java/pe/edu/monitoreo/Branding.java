package pe.edu.monitoreo;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.*;

/** Renders the official bundled SVG as native JavaFX paths, without network access. */
final class Branding {
    private record Path(String data,String fill){}
    private static final List<Path> PATHS=load();
    private static List<Path> load(){
        try(var in=Branding.class.getResourceAsStream("/images/plaza-vea.svg")){
            if(in==null)throw new IllegalStateException("Falta el logo de Plaza Vea.");
            var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            var nodes=factory.newDocumentBuilder().parse(in).getElementsByTagName("path");var paths=new ArrayList<Path>();
            for(int i=0;i<nodes.getLength();i++){var element=(org.w3c.dom.Element)nodes.item(i);paths.add(new Path(element.getAttribute("d"),element.getAttribute("fill")));}
            return List.copyOf(paths);
        }catch(Exception e){throw new IllegalStateException("No se pudo cargar el logo local.",e);}
    }
    static Node logo(double width){
        var paths=new Group();for(var entry:PATHS){var path=new SVGPath();path.setContent(entry.data());path.setFill(Color.web(entry.fill()));paths.getChildren().add(path);}
        paths.getTransforms().add(new Scale(width/103,width/103));
        var logo=new Group(paths);logo.setId("plaza-vea-logo");logo.setAccessibleText("Logo Plaza Vea");return logo;
    }
}
