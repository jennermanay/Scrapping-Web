package pe.edu.monitoreo.dao;
import pe.edu.monitoreo.Store.Product;
import java.sql.*;
import java.util.List;
/** Persists products using the caller transaction; never commits or owns the connection. */
public interface ProductDao {
    List<Product> findAll(Connection connection)throws SQLException;
    long save(Connection connection,Product product)throws SQLException;
}
