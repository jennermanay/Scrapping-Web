package pe.edu.monitoreo;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.net.URI;
import java.util.Properties;

public final class Config {
    private Config() {}
    public record Database(String url,String user,String password,int connectTimeout,int socketTimeout,String schema) {
        public Database(String url,String user,String password,int connectTimeout,int socketTimeout){this(url,user,password,connectTimeout,socketTimeout,"");}
        @Override public String toString(){return "PostgreSQL: "+url+" · usuario "+user;}
    }
    public static Path file(){return Path.of(System.getenv().getOrDefault("MONITOREO_CONFIG","config/database.properties")).toAbsolutePath().normalize();}
    public static Database database()throws IOException {return read(file());}
    public static Database read(Path path)throws IOException {
        if(!Files.isRegularFile(path))throw new IOException("Falta "+path+". Copia config/database.example.properties y completa la conexión.");
        var properties=new Properties();String text=Files.readString(path,StandardCharsets.UTF_8);if(text.startsWith("\ufeff"))text=text.substring(1);
        properties.load(new StringReader(text));
        String url=required(properties,"DB_URL"),user=required(properties,"DB_USER");
        if(!url.startsWith("jdbc:postgresql://"))throw new IllegalArgumentException("DB_URL debe comenzar con jdbc:postgresql://");
        try {var uri=URI.create(url.substring(5));if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getPath()==null||uri.getPath().length()<2)throw new IllegalArgumentException();
            String query=uri.getQuery();if(query!=null&&query.toLowerCase(java.util.Locale.ROOT).matches(".*(?:^|[&])(password|user)=.*"))throw new IllegalArgumentException();
        }catch(Exception e){throw new IllegalArgumentException("DB_URL requiere servidor y base, sin usuario ni contraseña dentro de la URL.");}
        String schema=properties.getProperty("DB_SCHEMA","").trim();
        if(!schema.isEmpty()&&!schema.matches("[a-z][a-z0-9_]{0,62}"))throw new IllegalArgumentException("DB_SCHEMA debe usar letras minúsculas, números y guion bajo, empezando por letra.");
        if(!schema.isEmpty()&&url.toLowerCase(java.util.Locale.ROOT).contains("currentschema="))throw new IllegalArgumentException("Usa DB_SCHEMA o currentSchema en DB_URL, sin combinar ambas opciones.");
        return new Database(url,user,properties.getProperty("DB_PASSWORD",""),timeout(properties,"CONNECT_TIMEOUT_SECONDS",5),timeout(properties,"SOCKET_TIMEOUT_SECONDS",15),schema);
    }
    private static String required(Properties p,String key){String value=p.getProperty(key,"").trim();if(value.isBlank())throw new IllegalArgumentException("Completa "+key+" en el archivo de conexión.");return value;}
    private static int timeout(Properties p,String key,int fallback){try{int value=Integer.parseInt(p.getProperty(key,""+fallback));if(value<1||value>300)throw new NumberFormatException();return value;}catch(NumberFormatException e){throw new IllegalArgumentException(key+" debe estar entre 1 y 300 segundos.");}}
}
