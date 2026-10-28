package pe.edu.monitoreo;
/** Modules granted to a role. PRODUCTOS includes catalog and Plaza Vea registration. */
public enum Module {
 INICIO("Inicio"),PRODUCTOS("Productos"),MONITOREO("Configurar monitoreo"),FUENTES("Fuentes"),FICHAS("Productos Homologados"),EXTRACCION("Extracción y homologación"),ALERTAS("Alertas"),REPORTES("Historial y reportes"),USUARIOS("Usuarios"),ROLES("Roles");
 private final String title;Module(String title){this.title=title;}@Override public String toString(){return title;}
}
