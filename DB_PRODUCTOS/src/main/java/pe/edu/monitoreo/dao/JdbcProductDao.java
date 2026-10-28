package pe.edu.monitoreo.dao;
import pe.edu.monitoreo.Store.Product;
import java.sql.*;
import java.util.*;
import static pe.edu.monitoreo.dao.JdbcStatements.*;
/** JDBC product and keyword persistence. The caller controls commit and rollback. */
public final class JdbcProductDao implements ProductDao {
    @Override public List<Product> findAll(Connection c)throws SQLException {
        var list=new ArrayList<Product>();
        try(var s=c.createStatement();var r=s.executeQuery("SELECT p.*,COALESCE(c.nombre,'General') categoria,COALESCE((SELECT string_agg(termino,', ' ORDER BY termino) FROM palabra_clave k WHERE k.producto_id=p.id),'') palabras FROM producto p LEFT JOIN categoria c ON c.id=p.categoria_id ORDER BY p.id")){
            while(r.next())list.add(new Product(r.getLong("id"),r.getString("codigo"),r.getString("nombre"),r.getString("marca"),r.getString("modelo"),r.getString("presentacion"),r.getString("capacidad"),r.getString("categoria"),r.getString("palabras"),r.getBigDecimal("costo"),r.getBigDecimal("precio"),r.getBoolean("activo")));
        }return list;
    }
    @Override public long save(Connection c,Product p)throws SQLException {
            String category=p.category().trim();
            update(c,"INSERT INTO categoria(nombre) VALUES (?) ON CONFLICT DO NOTHING",category);long categoryId;
            try(var s=prepare(c,"SELECT id FROM categoria WHERE nombre=?",category);var r=s.executeQuery()){r.next();categoryId=r.getLong(1);}
            long id=p.id();
            if(id==0)try(var s=prepare(c,"INSERT INTO producto(codigo,nombre,atributos,marca,modelo,presentacion,capacidad,categoria_id,costo,precio,activo) VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING id",p.code(),p.name(),p.attributes(),p.brand(),p.model(),p.presentation(),p.capacity(),categoryId,p.cost(),p.price(),p.active());var r=s.executeQuery()){r.next();id=r.getLong(1);}
            else update(c,"UPDATE producto SET codigo=?,nombre=?,atributos=?,marca=?,modelo=?,presentacion=?,capacidad=?,categoria_id=?,costo=?,precio=?,activo=? WHERE id=?",p.code(),p.name(),p.attributes(),p.brand(),p.model(),p.presentation(),p.capacity(),categoryId,p.cost(),p.price(),p.active(),id);
            update(c,"DELETE FROM palabra_clave WHERE producto_id=?",id);
            for(String keyword:p.keywords().split(","))if(!keyword.isBlank())update(c,"INSERT INTO palabra_clave(producto_id,termino) VALUES (?,?) ON CONFLICT DO NOTHING",id,keyword.trim().toLowerCase(Locale.ROOT));
            return id;
    }
}
