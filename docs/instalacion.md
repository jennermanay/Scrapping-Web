# Instalación y conexión PostgreSQL

Versiones utilizadas: JDK 21 de 64 bits, JavaFX 21.0.2, Jsoup 1.18.3, JDBC PostgreSQL 42.7.5, Gson 2.11.0, PDFBox 3.0.4, JUnit 5.11.4. El archivo pom.xml fija las versiones de librerías y plugins. El entorno comprobado es Windows con PostgreSQL 18 y Maven como mvn.cmd.

## Configurar un servidor real

El archivo editable es `config/database.properties`. La aplicación conecta directamente a PostgreSQL mediante JDBC; no utiliza memoria ni una base simulada. El arranque no instala PostgreSQL, no inicia un clúster ni crea bases. El servidor debe estar disponible y la base indicada debe existir.

```properties
DB_URL=jdbc:postgresql://localhost:5432/monitoreo_precios
DB_USER=postgres
DB_PASSWORD=TU_CLAVE
CONNECT_TIMEOUT_SECONDS=5
SOCKET_TIMEOUT_SECONDS=15
```

DB_URL contiene servidor, puerto y nombre de base. DB_USER y DB_PASSWORD son credenciales reales de PostgreSQL y son independientes de las cuentas de acceso de la aplicación. Si la clave contiene una barra inversa, escríbela doble según el formato .properties. El archivo admite UTF-8. No incluyas las credenciales dentro de DB_URL.

La configuración entregada conserva la conexión existente al servidor del proyecto en 127.0.0.1:55439, base monitoreo_precios, usuario monitoreo y su contraseña local. Puedes reemplazarla por tu instancia del puerto 5432, otro puerto o un servidor remoto. El archivo real está ignorado por Git; la plantilla `database.example.properties` no contiene credenciales válidas.

Para una conexión remota TLS, agrega `?sslmode=require`; si cuentas con la configuración de certificados y dominio, utiliza `?sslmode=verify-full`. La configuración `MONITOREO_CONFIG` permite indicar una ruta alternativa al archivo. Las variables antiguas DB_URL, DB_USER y DB_PASSWORD ya no sustituyen el archivo.

## Probar y recargar

Desde la pantalla de acceso entra a **Configuración de conexión**. Guarda los cambios en el archivo y pulsa **Probar conexión**. La prueba hace una conexión JDBC, valida respuesta y muestra versión, base y usuario sin aplicar migraciones. **Conectar y abrir** aplica el esquema en una transacción y vuelve al acceso correspondiente a esa base. Cambiar de conexión cierra la sesión; debes ingresar con una cuenta de la nueva base o crear el administrador si está vacía.

En terminal puedes ejecutar `scripts/Test-Connection.ps1`, que utiliza psql para consultar base, usuario y versión. Las utilidades de respaldo y preparación de ejemplos leen el mismo archivo.

## Abrir la aplicación

Ejecuta `Iniciar.cmd` o `scripts/Run.ps1`. El script usa `C:/Program Files/Java/jdk-21.0.12.1` si existe; en otro equipo configura JAVA_HOME con un JDK 21 de 64 bits. Maven requiere Internet para descargar dependencias la primera vez.

El usuario PostgreSQL necesita permisos de creación de tablas en su esquema porque **Conectar y abrir** aplica migraciones. El sistema no usa privilegios de superusuario para el trabajo habitual. Para otro esquema puedes configurar el parámetro JDBC currentSchema y preparar previamente ese esquema.

## Servidor local opcional

`scripts/Start-Database.ps1` y `scripts/Stop-Database.ps1` administran solamente el clúster aislado preparado anteriormente dentro de `.local/postgres`. Son utilidades opcionales y no forman parte del arranque normal. Start-Database conserva sus datos de administración en `.local/database.properties`; ese archivo no es la configuración que lee la aplicación.

Si trabajas con ese servidor, debes iniciarlo explícitamente cuando esté detenido. Para las pruebas puedes usar `scripts/Test.ps1 -LocalDatabase`; la suite seguirá utilizando la conexión indicada en `config/database.properties`.

## Diagnóstico

Conexión rechazada: revisa servidor encendido, host y puerto. Base inexistente: crea la base con un administrador o corrige DB_URL. Autenticación fallida: revisa usuario, clave y pg_hba.conf. Permisos insuficientes: concede acceso y permisos sobre el esquema indicado. La prueba de acceso puede pasar aunque el usuario no tenga permisos para migrar tablas.

Los tiempos máximos de conexión y espera se configuran en segundos entre 1 y 300. Una URL incorrecta no activa ninguna conexión alternativa silenciosa. Los errores se muestran en la aplicación sin imprimir la contraseña.
