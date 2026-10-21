package pe.edu.monitoreo;

public enum ReportType {
    COMPARACIONES("Comparación competitiva y márgenes","c.fecha",
        "SELECT c.fecha,c.producto_snapshot producto,c.atributos_snapshot atributos,f.nombre fuente,r.titulo candidato,r.estado homologacion,c.costo,c.propio,c.externo,c.diferencia,c.utilidad_actual,c.utilidad_simulada,c.margen_actual,c.margen_simulado,r.condicion,r.moneda,r.fecha capturado_en,r.url FROM comparacion c JOIN resultado r ON r.id=c.resultado_id JOIN ejecucion e ON e.id=r.ejecucion_id JOIN fuente f ON f.id=e.fuente_id"),
    HISTORIAL("Historial de precios capturados","r.fecha",
        "WITH serie AS (SELECT r0.*,r0.precio-lag(r0.precio) OVER(PARTITION BY e0.producto_id,e0.fuente_id ORDER BY r0.fecha,r0.id) variacion FROM resultado r0 JOIN ejecucion e0 ON e0.id=r0.ejecucion_id) SELECT r.fecha,p.nombre producto,f.nombre fuente,r.precio,r.variacion,r.estado homologacion,r.condicion,r.moneda,r.url FROM serie r JOIN ejecucion e ON e.id=r.ejecucion_id JOIN producto p ON p.id=e.producto_id JOIN fuente f ON f.id=e.fuente_id"),
    ALERTAS("Alertas competitivas","c.fecha",
        "SELECT c.fecha,c.producto_snapshot producto,f.nombre fuente,a.tipo,a.estado,u.correo responsable,a.observacion,a.atendida_en,c.propio,c.externo,r.condicion,r.fecha capturado_en,r.url FROM alerta a JOIN comparacion c ON c.id=a.comparacion_id JOIN resultado r ON r.id=c.resultado_id JOIN ejecucion e ON e.id=r.ejecucion_id JOIN fuente f ON f.id=e.fuente_id LEFT JOIN usuario u ON u.id=a.responsable_id"),
    EJECUCIONES("Ejecuciones y errores","e.inicio",
        "SELECT e.inicio,e.fin,p.nombre producto,f.nombre fuente,u.correo usuario,e.estado,(SELECT count(*) FROM resultado x WHERE x.ejecucion_id=e.id) cantidad,e.error,e.url FROM ejecucion e JOIN producto p ON p.id=e.producto_id JOIN fuente f ON f.id=e.fuente_id LEFT JOIN usuario u ON u.id=e.usuario_id"),
    HOMOLOGACIONES("Homologaciones y evidencias","r.fecha",
        "SELECT r.fecha,p.nombre producto,p.atributos,r.titulo candidato,f.nombre fuente,r.estado,r.responsable,r.observacion,r.validado_en,r.precio,r.condicion,r.evidencia,r.url FROM resultado r JOIN ejecucion e ON e.id=r.ejecucion_id JOIN producto p ON p.id=e.producto_id JOIN fuente f ON f.id=e.fuente_id"),
    EXPORTACIONES("Registro de exportaciones","q.fecha",
        "SELECT q.fecha,u.correo usuario,q.tipo,q.parametros,q.archivo FROM reporte q JOIN usuario u ON u.id=q.usuario_id LEFT JOIN ejecucion e ON FALSE");
    private final String title,date,sql;
    ReportType(String title,String date,String sql){this.title=title;this.date=date;this.sql=sql;}
    public String sql(){return sql;} public String dateColumn(){return date;}
    @Override public String toString(){return title;}
}
