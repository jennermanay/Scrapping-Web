package pe.edu.monitoreo;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import com.google.gson.Gson;

/** Repository and transactional application boundary. Every operation checks the session. */
public final class Store {
    public record User(long id,String email,String name,String role) { public boolean admin(){return role.equals("ADMIN");} @Override public String toString(){return name+" · "+role;} }
    public record Product(long id,String code,String name,String brand,String model,String presentation,String capacity,String category,String keywords,BigDecimal cost,BigDecimal price,boolean active) {
        public String attributes(){return String.join(" · ",brand,model,presentation,capacity);}
        @Override public String toString(){return code+" · "+name+(active?"":" [INACTIVO]");}
    }
    public record Source(long id,String name,String url,String titleSelector,String priceSelector,String titleAttribute,String priceAttribute,String strategy,int interval,int validity,boolean active) {
        @Override public String toString(){return name+" · "+strategy+(active?"":" [INACTIVA]");}
    }
    public record Link(long id,long productId,long sourceId,String product,String source,String url,String condition,boolean scheduled,boolean active) {
        @Override public String toString(){return "#"+id+" · "+product+" / "+source+(scheduled?" [PROGRAMADO]":"")+(active?"":" [INACTIVO]");}
    }
    public record Candidate(long id,String title,BigDecimal price,String url,String status,String condition,OffsetDateTime date,String evidence) {
        @Override public String toString(){return "#"+id+" · "+title+" · S/ "+price+" · "+status+" · "+date;}
    }
    public record AlertItem(long id,String product,String type,String status,String note) { @Override public String toString(){return "#"+id+" · "+product+" · "+type+" · "+status+" · "+note;} }
    private volatile Config.Database connection;
    private final boolean fromFile;
    private volatile User session;
    public Store(){fromFile=true;}
    public Store(String url,String user,String password){connection=new Config.Database(url,user,password,5,15);fromFile=false;}
    public synchronized void reloadConfiguration()throws SQLException {
        if(!fromFile)return;
        session=null;connection=null;
        try {connection=Config.database();}catch(Exception e){throw new SQLException(e.getMessage(),e);}
    }
    Connection open() throws SQLException {
        if(connection==null)reloadConfiguration();var settings=connection;
        var properties=new Properties();properties.setProperty("user",settings.user());properties.setProperty("password",settings.password());
        properties.setProperty("connectTimeout",""+settings.connectTimeout());properties.setProperty("socketTimeout",""+settings.socketTimeout());
        var c=DriverManager.getConnection(settings.url(),properties);c.setNetworkTimeout(Runnable::run,settings.socketTimeout()*1000);return c;
    }
    public String testConnection()throws SQLException {try(var c=open()){if(!c.isValid(3))throw new SQLException("La conexión no respondió.");return "Conexión correcta · PostgreSQL "+c.getMetaData().getDatabaseProductVersion()+" · base "+c.getCatalog()+" · usuario "+c.getMetaData().getUserName();}}
    public User session(){return session;}
    private User require() throws SQLException {
        var u=session; if(u==null) throw new SecurityException("Inicia sesión.");
        try(var c=open();var s=prepare(c,"SELECT activo,rol FROM usuario WHERE id=?",u.id());var r=s.executeQuery()){
            if(!r.next()||!r.getBoolean(1)||!u.role().equals(r.getString(2))) {session=null;throw new SecurityException("La sesión perdió sus permisos. Ingresa nuevamente.");}
        }return u;
    }
    private User admin() throws SQLException {var u=require();if(!u.admin())throw new SecurityException("Se requiere rol administrador.");return u;}
    public void initialize() throws Exception {
        try(var c=open()) {c.setAutoCommit(false);try(var lock=c.createStatement()){lock.execute("SELECT pg_advisory_xact_lock(729513)");}
            try { for(String file:List.of("/schema.sql","/migration-v2.sql","/migration-v3.sql"))try(var in=Store.class.getResourceAsStream(file);var s=c.createStatement()){
                if(in==null)throw new IllegalStateException("Falta "+file);s.execute(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            }c.commit();}catch(Exception e){c.rollback();throw e;}
        }
    }
    public boolean needsSetup() throws SQLException {try(var c=open();var s=c.createStatement();var r=s.executeQuery("SELECT NOT EXISTS(SELECT 1 FROM usuario)")){r.next();return r.getBoolean(1);}}
    public void setup(String email,String name,String password)throws SQLException {
        String hash=Passwords.hash(password);email=validEmail(email);name=required(name,"Nombre");
        try(var c=open()){c.setAutoCommit(false);try{
            try(var s=c.createStatement()){s.execute("SELECT pg_advisory_xact_lock(729514)");}
            try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM usuario")){r.next();if(r.getInt(1)!=0)throw new IllegalStateException("El administrador inicial ya existe.");}
            update(c,"INSERT INTO usuario(correo,nombre,password_hash,rol) VALUES (?,?,?,'ADMIN')",email,name,hash);c.commit();
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public User login(String email,String password)throws SQLException {
        session=null;
        try(var c=open();var s=prepare(c,"SELECT * FROM usuario WHERE correo=? AND activo",email.trim().toLowerCase(Locale.ROOT));var r=s.executeQuery()){
            if(!r.next()||!Passwords.verify(password,r.getString("password_hash")))throw new SecurityException("Credenciales inválidas.");
            session=new User(r.getLong("id"),r.getString("correo"),r.getString("nombre"),r.getString("rol"));return session;
        }
    }
    public void logout(){session=null;}
    public List<User> users()throws SQLException {admin();var list=new ArrayList<User>();try(var c=open();var s=c.createStatement();var r=s.executeQuery("SELECT * FROM usuario ORDER BY id")){while(r.next())list.add(new User(r.getLong("id"),r.getString("correo"),r.getString("nombre")+(r.getBoolean("activo")?"":" [INACTIVO]"),r.getString("rol")));}return list;}
    public void saveUser(long id,String email,String name,String password,String role,boolean active)throws SQLException {
        var actor=admin();if(!Set.of("ADMIN","ANALISTA").contains(role))throw new IllegalArgumentException("Rol inválido.");
        if(id==actor.id()&&(!active||!role.equals("ADMIN")))throw new IllegalArgumentException("No puedes quitar tus propios permisos administrativos.");
        email=validEmail(email);name=required(name,"Nombre");
        try(var c=open()){if(id==0)update(c,"INSERT INTO usuario(correo,nombre,password_hash,rol,activo) VALUES (?,?,?,?,?)",email,name,Passwords.hash(password),role,active);
            else {update(c,"UPDATE usuario SET correo=?,nombre=?,rol=?,activo=? WHERE id=?",email,name,role,active,id);if(!password.isEmpty())update(c,"UPDATE usuario SET password_hash=? WHERE id=?",Passwords.hash(password),id);}}
    }
    public List<Product> products()throws SQLException {
        require();var list=new ArrayList<Product>();
        try(var c=open();var s=c.createStatement();var r=s.executeQuery("SELECT p.*,COALESCE(c.nombre,'General') categoria,COALESCE((SELECT string_agg(termino,', ' ORDER BY termino) FROM palabra_clave k WHERE k.producto_id=p.id),'') palabras FROM producto p LEFT JOIN categoria c ON c.id=p.categoria_id ORDER BY p.id")){
            while(r.next())list.add(new Product(r.getLong("id"),r.getString("codigo"),r.getString("nombre"),r.getString("marca"),r.getString("modelo"),r.getString("presentacion"),r.getString("capacidad"),r.getString("categoria"),r.getString("palabras"),r.getBigDecimal("costo"),r.getBigDecimal("precio"),r.getBoolean("activo")));
        }return list;
    }
    public Product product(long id)throws SQLException {return products().stream().filter(p->p.id()==id).findFirst().orElseThrow(()->new IllegalArgumentException("Producto inexistente."));}
    public void saveProduct(Product p)throws SQLException {
        admin();required(p.code(),"Código");required(p.name(),"Nombre");required(p.brand(),"Marca");required(p.presentation(),"Presentación");required(p.capacity(),"Capacidad");Economics.compare(p.cost(),p.price(),p.price());
        String category=required(p.category(),"Categoría");
        try(var c=open()){c.setAutoCommit(false);try{
            update(c,"INSERT INTO categoria(nombre) VALUES (?) ON CONFLICT DO NOTHING",category);long categoryId;
            try(var s=prepare(c,"SELECT id FROM categoria WHERE nombre=?",category);var r=s.executeQuery()){r.next();categoryId=r.getLong(1);}
            long id=p.id();
            if(id==0)try(var s=prepare(c,"INSERT INTO producto(codigo,nombre,atributos,marca,modelo,presentacion,capacidad,categoria_id,costo,precio,activo) VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING id",p.code(),p.name(),p.attributes(),p.brand(),p.model(),p.presentation(),p.capacity(),categoryId,p.cost(),p.price(),p.active());var r=s.executeQuery()){r.next();id=r.getLong(1);}
            else update(c,"UPDATE producto SET codigo=?,nombre=?,atributos=?,marca=?,modelo=?,presentacion=?,capacidad=?,categoria_id=?,costo=?,precio=?,activo=? WHERE id=?",p.code(),p.name(),p.attributes(),p.brand(),p.model(),p.presentation(),p.capacity(),categoryId,p.cost(),p.price(),p.active(),id);
            update(c,"DELETE FROM palabra_clave WHERE producto_id=?",id);
            for(String keyword:p.keywords().split(","))if(!keyword.isBlank())update(c,"INSERT INTO palabra_clave(producto_id,termino) VALUES (?,?) ON CONFLICT DO NOTHING",id,keyword.trim().toLowerCase(Locale.ROOT));
            c.commit();
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public List<Source> sources()throws SQLException {
        require();var list=new ArrayList<Source>();try(var c=open();var s=c.createStatement();var r=s.executeQuery("SELECT * FROM fuente ORDER BY id")){
            while(r.next())list.add(new Source(r.getLong("id"),r.getString("nombre"),r.getString("url"),r.getString("selector_titulo"),r.getString("selector_precio"),r.getString("atributo_titulo"),r.getString("atributo_precio"),r.getString("estrategia"),r.getInt("intervalo"),r.getInt("vigencia"),r.getBoolean("activo")));
        }return list;
    }
    public Source source(long id)throws SQLException {return sources().stream().filter(s->s.id()==id).findFirst().orElseThrow(()->new IllegalArgumentException("Fuente inexistente."));}
    public void saveSource(Source s)throws SQLException {
        admin();required(s.name(),"Nombre de fuente");Extractor.validateUrl(s.url(),s.strategy().equals("DEMO"));
        if(!Set.of("CSS","VTEX","JSON_LD","DEMO").contains(s.strategy())||s.interval()<15||s.validity()<1||s.validity()>168)throw new IllegalArgumentException("Estrategia, intervalo o vigencia inválidos.");
        if(s.strategy().equals("CSS")){required(s.titleSelector(),"Selector de título");required(s.priceSelector(),"Selector de precio");org.jsoup.Jsoup.parse("").select(s.titleSelector());org.jsoup.Jsoup.parse("").select(s.priceSelector());}
        try(var c=open()){if(s.id()==0)update(c,"INSERT INTO fuente(nombre,url,selector_titulo,selector_precio,atributo_titulo,atributo_precio,estrategia,intervalo,vigencia,activo) VALUES (?,?,?,?,?,?,?,?,?,?)",s.name(),s.url(),s.titleSelector(),s.priceSelector(),s.titleAttribute(),s.priceAttribute(),s.strategy(),s.interval(),s.validity(),s.active());
            else update(c,"UPDATE fuente SET nombre=?,url=?,selector_titulo=?,selector_precio=?,atributo_titulo=?,atributo_precio=?,estrategia=?,intervalo=?,vigencia=?,activo=?,revisado_en=CURRENT_TIMESTAMP WHERE id=?",s.name(),s.url(),s.titleSelector(),s.priceSelector(),s.titleAttribute(),s.priceAttribute(),s.strategy(),s.interval(),s.validity(),s.active(),s.id());}
    }
    public List<Link> links()throws SQLException {
        require();var list=new ArrayList<Link>();try(var c=open();var s=c.createStatement();var r=s.executeQuery("SELECT pf.*,p.nombre producto,f.nombre fuente FROM producto_fuente pf JOIN producto p ON p.id=pf.producto_id JOIN fuente f ON f.id=pf.fuente_id ORDER BY pf.id")){
            while(r.next())list.add(new Link(r.getLong("id"),r.getLong("producto_id"),r.getLong("fuente_id"),r.getString("producto"),r.getString("fuente"),r.getString("url"),r.getString("condicion"),r.getBoolean("programado"),r.getBoolean("activo")));
        }return list;
    }
    public void saveLink(long id,long product,long source,String url,String condition,boolean scheduled,boolean active)throws SQLException {
        admin();var f=source(source);Extractor.validateUrl(url,f.strategy().equals("DEMO"));required(condition,"Condición de oferta");
        if(!f.strategy().equals("DEMO")&&!java.net.URI.create(url).getHost().equalsIgnoreCase(java.net.URI.create(f.url()).getHost()))throw new IllegalArgumentException("La ficha debe pertenecer al dominio configurado en la fuente.");
        try(var c=open()){if(id==0)update(c,"INSERT INTO producto_fuente(producto_id,fuente_id,url,condicion,programado,activo) VALUES (?,?,?,?,?,?)",product,source,url,condition,scheduled,active);
            else update(c,"UPDATE producto_fuente SET producto_id=?,fuente_id=?,url=?,condicion=?,programado=?,activo=? WHERE id=?",product,source,url,condition,scheduled,active,id);}
    }
    public List<Link> dueLinks()throws SQLException {
        require();var ids=new HashSet<Long>();try(var c=open();var s=c.createStatement();var r=s.executeQuery("SELECT pf.id FROM producto_fuente pf JOIN producto p ON p.id=pf.producto_id JOIN fuente f ON f.id=pf.fuente_id WHERE pf.programado AND pf.activo AND p.activo AND f.activo AND pf.proxima_ejecucion<=CURRENT_TIMESTAMP")){while(r.next())ids.add(r.getLong(1));}
        return links().stream().filter(l->ids.contains(l.id())).toList();
    }
    public long start(Link link)throws SQLException {
        var user=require();try(var c=open()){c.setAutoCommit(false);try{
            try(var s=prepare(c,"SELECT p.activo AND f.activo AND pf.activo habilitado,f.intervalo FROM producto_fuente pf JOIN producto p ON p.id=pf.producto_id JOIN fuente f ON f.id=pf.fuente_id WHERE pf.id=? FOR UPDATE OF pf",link.id());var r=s.executeQuery()){
                if(!r.next()||!r.getBoolean(1))throw new IllegalArgumentException("Producto, fuente o vínculo inactivo.");
                update(c,"UPDATE producto_fuente SET proxima_ejecucion=CURRENT_TIMESTAMP + (? * interval '1 minute') WHERE id=?",r.getInt(2),link.id());
            }
            long id;try(var s=prepare(c,"INSERT INTO ejecucion(producto_id,fuente_id,usuario_id,vinculo_id,url) VALUES (?,?,?,?,?) RETURNING id",link.productId(),link.sourceId(),user.id(),link.id(),link.url());var r=s.executeQuery()){r.next();id=r.getLong(1);}c.commit();return id;
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public void success(long run,Extractor.Quote quote,String condition)throws SQLException {
        require();try(var c=open()){c.setAutoCommit(false);try{
            long id;try(var s=prepare(c,"INSERT INTO resultado(ejecucion_id,titulo,precio,url,condicion,evidencia) VALUES (?,?,?,?,?,?) RETURNING id",run,quote.title(),quote.price(),quote.url(),condition,quote.evidence());var r=s.executeQuery()){r.next();id=r.getLong(1);}
            update(c,"INSERT INTO historico_precio(resultado_id,producto_id,fuente_id,precio,fecha,url) SELECT r.id,e.producto_id,e.fuente_id,r.precio,r.fecha,r.url FROM resultado r JOIN ejecucion e ON e.id=r.ejecucion_id WHERE r.id=?",id);
            update(c,"UPDATE ejecucion SET estado='OK',fin=CURRENT_TIMESTAMP WHERE id=?",run);c.commit();
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public void fail(long run,String error)throws SQLException {require();try(var c=open()){update(c,"UPDATE ejecucion SET estado='ERROR',fin=CURRENT_TIMESTAMP,error=? WHERE id=?",error,run);}}
    public List<Candidate> candidates(long product)throws SQLException {
        require();var list=new ArrayList<Candidate>();try(var c=open();var s=prepare(c,"SELECT r.* FROM resultado r JOIN ejecucion e ON e.id=r.ejecucion_id WHERE e.producto_id=? ORDER BY r.id DESC",product);var r=s.executeQuery()){
            while(r.next())list.add(new Candidate(r.getLong("id"),r.getString("titulo"),r.getBigDecimal("precio"),r.getString("url"),r.getString("estado"),r.getString("condicion"),r.getObject("fecha",OffsetDateTime.class),r.getString("evidencia")));
        }return list;
    }
    public void validate(long id,boolean confirmed,String note)throws SQLException {
        var user=require();required(note,"Evidencia de revisión");String state=confirmed?"CONFIRMADO":"RECHAZADO";
        try(var c=open()){c.setAutoCommit(false);try{
            if(update(c,"UPDATE resultado SET estado=?,responsable=?,observacion=?,validado_en=CURRENT_TIMESTAMP WHERE id=? AND estado='PENDIENTE'",state,user.email(),note,id)!=1)throw new IllegalStateException("El candidato ya fue revisado.");
            update(c,"INSERT INTO homologacion(resultado_id,usuario_id,estado,observacion) VALUES (?,?,?,?)",id,user.id(),state,note);c.commit();
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public Economics.Result compare(long id)throws SQLException {
        var user=require();try(var c=open()){c.setAutoCommit(false);try{
            Economics.Result result;
            try(var s=prepare(c,"SELECT p.*,r.precio externo,r.fecha,f.vigencia FROM resultado r JOIN homologacion h ON h.resultado_id=r.id AND h.estado='CONFIRMADO' JOIN ejecucion e ON e.id=r.ejecucion_id JOIN producto p ON p.id=e.producto_id JOIN fuente f ON f.id=e.fuente_id WHERE r.id=? AND r.estado='CONFIRMADO' AND p.activo AND f.activo FOR UPDATE OF r,p",id);var r=s.executeQuery()){
                if(!r.next())throw new IllegalArgumentException("Se requiere homologación confirmada y catálogo activo.");
                if(r.getObject("fecha",OffsetDateTime.class).plusHours(r.getInt("vigencia")).isBefore(OffsetDateTime.now()))throw new IllegalArgumentException("Precio vencido. Ejecuta una nueva consulta.");
                var cost=r.getBigDecimal("costo");var own=r.getBigDecimal("precio");var external=r.getBigDecimal("externo");result=Economics.compare(cost,own,external);
                String type=result.loss()?"PERDIDA":result.lower()?"PRECIO_INFERIOR":"SIN_ALERTA";long comparison;
                try(var save=prepare(c,"INSERT INTO comparacion(resultado_id,costo,propio,externo,diferencia,utilidad_actual,utilidad_simulada,margen_actual,margen_simulado,alerta,usuario_id,producto_snapshot,atributos_snapshot) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(resultado_id) DO NOTHING RETURNING id",id,cost,own,external,result.difference(),result.currentProfit(),result.simulatedProfit(),result.currentMargin(),result.simulatedMargin(),type,user.id(),r.getString("nombre"),r.getString("atributos"));var saved=save.executeQuery()){
                    if(!saved.next())throw new IllegalStateException("El resultado ya tiene comparación histórica.");comparison=saved.getLong(1);
                }
                if(!type.equals("SIN_ALERTA"))update(c,"INSERT INTO alerta(comparacion_id,tipo) VALUES (?,?)",comparison,type);
            }c.commit();return result;
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public List<AlertItem> alerts()throws SQLException {
        require();var list=new ArrayList<AlertItem>();try(var c=open();var s=c.createStatement();var r=s.executeQuery("SELECT a.*,c.producto_snapshot FROM alerta a JOIN comparacion c ON c.id=a.comparacion_id ORDER BY a.id DESC")){while(r.next())list.add(new AlertItem(r.getLong("id"),r.getString("producto_snapshot"),r.getString("tipo"),r.getString("estado"),r.getString("observacion")));}return list;
    }
    public void attend(long id,String state,String note)throws SQLException {
        var u=require();if(!Set.of("EN_REVISION","ATENDIDA","DESCARTADA").contains(state))throw new IllegalArgumentException("Estado inválido.");required(note,"Observación");
        try(var c=open()){c.setAutoCommit(false);try{
            if(update(c,"UPDATE alerta SET estado=?,observacion=?,responsable_id=?,atendida_en=CURRENT_TIMESTAMP WHERE id=?",state,note,u.id(),id)!=1)throw new IllegalArgumentException("Alerta inexistente.");
            update(c,"INSERT INTO alerta_atencion(alerta_id,usuario_id,estado,observacion) VALUES (?,?,?,?)",id,u.id(),state,note);c.commit();
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public ReportData report(ReportType type,ReportFilter filter)throws SQLException {
        require();if(filter.from()!=null&&filter.to()!=null&&filter.from().isAfter(filter.to()))throw new IllegalArgumentException("Rango de fechas inválido.");
        var rows=new ArrayList<List<String>>();var columns=new ArrayList<String>();
        var lima=ZoneId.of("America/Lima");
        OffsetDateTime from=filter.from()==null?OffsetDateTime.parse("1970-01-01T00:00:00Z"):filter.from().atStartOfDay(lima).toOffsetDateTime();
        OffsetDateTime to=filter.to()==null?OffsetDateTime.parse("9999-01-01T00:00:00Z"):filter.to().plusDays(1).atStartOfDay(lima).toOffsetDateTime();
        try(var c=open();var s=prepare(c,type.sql()+" WHERE (?=0 OR e.producto_id=?) AND (?=0 OR e.fuente_id=?) AND "+type.dateColumn()+">=? AND "+type.dateColumn()+"<? ORDER BY "+type.dateColumn()+" DESC",filter.product(),filter.product(),filter.source(),filter.source(),from,to);var r=s.executeQuery()){
            var meta=r.getMetaData();for(int i=1;i<=meta.getColumnCount();i++)columns.add(meta.getColumnLabel(i));
            while(r.next()){var row=new ArrayList<String>();for(int i=1;i<=columns.size();i++){
                Object value=r.getObject(i);
                if(value!=null&&meta.getColumnTypeName(i).equals("timestamptz"))value=r.getObject(i,OffsetDateTime.class).atZoneSameInstant(lima).toOffsetDateTime();
                row.add(Objects.toString(value,""));
            }rows.add(row);}
        }return new ReportData(type.toString(),List.copyOf(columns),List.copyOf(rows));
    }
    public void recordExport(ReportType type,ReportFilter filter,String path)throws SQLException {var u=require();try(var c=open()){update(c,"INSERT INTO reporte(usuario_id,tipo,parametros,archivo) VALUES (?,?,?::jsonb,?)",u.id(),type.name(),new Gson().toJson(Map.of("producto",filter.product(),"fuente",filter.source(),"desde",Objects.toString(filter.from(),""),"hasta",Objects.toString(filter.to(),""))),path);}}
    public static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException("Completa: "+field);return value.trim();}
    private static String validEmail(String email){email=required(email,"Correo").toLowerCase(Locale.ROOT);if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new IllegalArgumentException("Correo inválido.");return email;}
    private static PreparedStatement prepare(Connection c,String sql,Object... values)throws SQLException {var s=c.prepareStatement(sql);s.setQueryTimeout(12);for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);return s;}
    private static int update(Connection c,String sql,Object... values)throws SQLException {try(var s=prepare(c,sql,values)){return s.executeUpdate();}}
}
