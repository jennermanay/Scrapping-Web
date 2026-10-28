package pe.edu.monitoreo.dao;
import pe.edu.monitoreo.Store.Source;
import java.sql.*;
import java.util.List;
/** Persistence contract for sources. Session authorization belongs to the application boundary. */
public interface SourceDao {
    List<Source> findAll(Connection connection)throws SQLException;
    void save(Connection connection,Source source)throws SQLException;
}
