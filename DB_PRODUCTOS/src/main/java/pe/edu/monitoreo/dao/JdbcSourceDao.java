package pe.edu.monitoreo.dao;
import pe.edu.monitoreo.Store.Source;
import java.sql.*;
import java.util.*;
import static pe.edu.monitoreo.dao.JdbcStatements.*;
/** JDBC source persistence with bound values. */
public final class JdbcSourceDao implements SourceDao {
    @Override public List<Source> findAll(Connection c)throws SQLException {
        var list=new ArrayList<Source>();try(var s=c.createStatement();var r=s.executeQuery("SELECT * FROM fuente ORDER BY id")){
            while(r.next())list.add(new Source(r.getLong("id"),r.getString("nombre"),r.getString("url"),r.getString("selector_titulo"),r.getString("selector_precio"),r.getString("atributo_titulo"),r.getString("atributo_precio"),r.getString("estrategia"),r.getInt("intervalo"),r.getInt("vigencia"),r.getBoolean("activo")));
        }return list;
    }
    @Override public void save(Connection c,Source s)throws SQLException {
        {if(s.id()==0)update(c,"INSERT INTO fuente(nombre,url,selector_titulo,selector_precio,atributo_titulo,atributo_precio,estrategia,intervalo,vigencia,activo) VALUES (?,?,?,?,?,?,?,?,?,?)",s.name(),s.url(),s.titleSelector(),s.priceSelector(),s.titleAttribute(),s.priceAttribute(),s.strategy(),s.interval(),s.validity(),s.active());
            else update(c,"UPDATE fuente SET nombre=?,url=?,selector_titulo=?,selector_precio=?,atributo_titulo=?,atributo_precio=?,estrategia=?,intervalo=?,vigencia=?,activo=?,revisado_en=CURRENT_TIMESTAMP WHERE id=?",s.name(),s.url(),s.titleSelector(),s.priceSelector(),s.titleAttribute(),s.priceAttribute(),s.strategy(),s.interval(),s.validity(),s.active(),s.id());}
    }
}
