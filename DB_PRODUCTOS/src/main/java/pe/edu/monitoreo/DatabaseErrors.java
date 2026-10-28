package pe.edu.monitoreo;

import java.sql.SQLException;

public final class DatabaseErrors {
    private DatabaseErrors() {}
    public static String explain(Throwable error) {
        SQLException sql=null;
        for(Throwable cause=error;cause!=null;cause=cause.getCause())if(cause instanceof SQLException s&&s.getSQLState()!=null){sql=s;break;}
        if(sql==null)return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();
        String detail=switch(sql.getSQLState()) {
            case "28P01","28000" -> "El servidor PostgreSQL responde, pero rechaza el usuario o la contraseña de DB_USER/DB_PASSWORD. Verifica que sean los de esta instancia; no son las credenciales de acceso a la aplicación.";
            case "3D000" -> "La conexión alcanza PostgreSQL, pero la base indicada en DB_URL no existe. Crea esa base o corrige su nombre.";
            case "42501" -> "La autenticación funciona, pero el usuario no tiene permisos para preparar o consultar el esquema del sistema.";
            case "42P01","42703","42P16","42710" -> "La conexión funciona, pero el esquema tiene tablas, columnas o vistas incompatibles. Revisa la preparación del esquema; no cambies la contraseña por este error.";
            default -> sql.getSQLState().startsWith("08")?"No se pudo establecer o mantener la conexión. Comprueba servidor encendido, puerto, TLS y acceso de red.":"PostgreSQL rechazó la operación. Revisa el esquema y el detalle registrado.";
        };
        return detail+"\nSQLSTATE: "+sql.getSQLState()+"\nArchivo: "+Config.file();
    }
}
