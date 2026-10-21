package pe.edu.monitoreo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
    @TempDir Path dir;
    @Test void editableUtf8Configuration()throws Exception {
        var path=dir.resolve("database.properties");Files.writeString(path,"\ufeffDB_URL=jdbc:postgresql://servidor:5433/precios?sslmode=require\nDB_USER=operador\nDB_PASSWORD=contraseña=prueba\nCONNECT_TIMEOUT_SECONDS=7\nSOCKET_TIMEOUT_SECONDS=20\n");
        var settings=Config.read(path);assertEquals("operador",settings.user());assertEquals("contraseña=prueba",settings.password());assertEquals(7,settings.connectTimeout());assertEquals(20,settings.socketTimeout());assertFalse(settings.toString().contains(settings.password()));
        Files.writeString(path,"DB_URL=jdbc:postgresql://otro:5432/catalogo\nDB_USER=otro\nDB_PASSWORD=nueva\n");assertTrue(Config.read(path).url().contains("otro:5432/catalogo"));
    }
    @Test void missingConfigurationHasNoSilentFallback(){assertThrows(IOException.class,()->Config.read(dir.resolve("missing.properties")));}
    @Test void rejectsCredentialsInUrlAndInvalidTimeout()throws Exception {
        var path=dir.resolve("database.properties");
        for(String url:new String[]{"jdbc:mysql://localhost/precios","jdbc:postgresql://u:p@host/base","jdbc:postgresql://host/base?password=secret"}){Files.writeString(path,"DB_URL="+url+"\nDB_USER=operador\n");assertThrows(IllegalArgumentException.class,()->Config.read(path));}
        Files.writeString(path,"DB_URL=jdbc:postgresql://localhost/precios\nDB_USER=operador\nCONNECT_TIMEOUT_SECONDS=0\n");assertThrows(IllegalArgumentException.class,()->Config.read(path));
    }
}
