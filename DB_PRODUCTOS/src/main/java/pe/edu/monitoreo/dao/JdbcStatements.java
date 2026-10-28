package pe.edu.monitoreo.dao;
import java.sql.*;
/** Binds values instead of interpolating SQL and applies a bounded query timeout. */
final class JdbcStatements {
    private JdbcStatements(){}
    static PreparedStatement prepare(Connection c,String sql,Object... values)throws SQLException {var statement=c.prepareStatement(sql);try{statement.setQueryTimeout(20);for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);return statement;}catch(SQLException e){statement.close();throw e;}}
    static void update(Connection c,String sql,Object... values)throws SQLException {try(var statement=prepare(c,sql,values)){statement.executeUpdate();}}
}
