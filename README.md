# Monitoreo competitivo de precios

Aplicación de escritorio en Java 21 con JavaFX, Jsoup, JDBC y PostgreSQL. Implementa acceso por roles, catálogo, fuentes configurables, extracción de fichas reales, homologación, comparación económica, alertas, históricos y exportación PDF/CSV. Los costos y precios propios de los ejemplos son ficticios y editables.

## Iniciar en esta computadora

Abre `Iniciar.cmd` o ejecuta en PowerShell desde esta carpeta:

```powershell
.\scripts\Run.ps1
```

El script usa el JDK 21 instalado y se conecta al servidor PostgreSQL indicado en **`config/database.properties`**. No crea servidores ni bases automáticamente. El archivo actual conserva la conexión real que ya funciona en **127.0.0.1:55439/monitoreo_precios**. Maven requiere Internet la primera vez para descargar dependencias.

Para usar otro servidor, edita y guarda ese archivo:

```properties
DB_URL=jdbc:postgresql://localhost:5432/monitoreo_precios
DB_USER=postgres
DB_PASSWORD=TU_CLAVE
CONNECT_TIMEOUT_SECONDS=5
SOCKET_TIMEOUT_SECONDS=15
```

El bloque anterior es un ejemplo: sustituye los valores por los de tu servidor y crea la base previamente si no existe. Desde el acceso pulsa **Configuración de conexión**, luego **Probar conexión** para recargar el archivo y comprobar el acceso sin modificar tablas. **Conectar y abrir** aplica el esquema y abre el acceso. También puedes usar `scripts/Test-Connection.ps1`. El archivo real está excluido de Git; `config/database.example.properties` es la plantilla compartible. No se utilizan valores DB_URL/DB_USER/DB_PASSWORD del entorno para reemplazar silenciosamente el archivo. `MONITOREO_CONFIG` permite elegir otro archivo explícitamente.

En el primer acceso registra tu nombre, correo y contraseña de al menos 10 caracteres. Esa cuenta será administradora. Crea cuentas de analista en **Usuarios**. No existen contraseñas de aplicación predeterminadas.

## Probar con páginas reales

1. En **Inicio**, pulsa **Cargar fichas reales: Plaza Vea y Makro**. Se registra Leche Entera UHT GLORIA Caja 946ml y dos fuentes. Los costos y precios propios de muestra se pueden cambiar en **Productos**.
2. En **Extracción y homologación**, selecciona una ficha y pulsa **Probar extractor sin guardar precio** o **Extraer ficha seleccionada**.
3. Selecciona el candidato. Abre la URL en tu navegador y verifica título, capacidad, presentación, condición comercial y ubicación. El slug de la URL aún dice 1 L, pero el título consultado corresponde a 946 ml: la equivalencia se revisa con el contenido actual.
4. Escribe la evidencia y confirma o rechaza. Confirmar no compara automáticamente.
5. Selecciona nuevamente el candidato confirmado y pulsa **Comparar y generar alerta**. Se guardan costo, precios y márgenes de esa fecha.
6. En **Alertas**, registra estado y observación. En **Historial y reportes**, consulta por producto/fuente/fechas y exporta CSV o PDF.

Las fichas precargadas son:

- [Plaza Vea, leche GLORIA 946ml](https://www.plazavea.com.pe/leche-gloria-entera-uhtcon-vitamina-a-cy-d-caja-1l/p).
- [Makro, leche GLORIA 946ml](https://www.makro.plazavea.com.pe/leche-gloria-entera-uhtcon-vitamina-a-cy-d-caja-1l/p).

Son dos canales de la plataforma Plaza Vea; no representan una muestra de competidores independientes. La consulta real se verificó el 21 de octubre de 2026. Los precios pueden cambiar y depender de ubicación, canal, stock o promociones. Cada nueva extracción registra el valor recibido; no se utiliza un precio fijo de ejemplo como precio externo real.

## Añadir tus propios productos y páginas

En **Productos**, registra código único, nombre, marca, variedad/modelo, presentación, capacidad, categoría, palabras clave, costo y precio propio. En **Fuentes**, registra el dominio y una estrategia:

| Estrategia | Cuándo utilizarla |
|---|---|
| `VTEX` | HTML público con un objeto `skuJson` y una sola variante disponible, como las fichas precargadas. |
| `CSS` | HTML con título y precio identificables por selectores únicos. Permite leer texto o un atributo como `content`. |
| `JSON_LD` | HTML con un único `Product` JSON-LD y una oferta individual que declare moneda PEN. |
| `DEMO` | Solo `demo://producto`; datos ficticios para practicar sin conexión externa. |

En **Fichas a monitorear**, vincula el producto con la fuente y la URL exacta. El dominio debe coincidir con la fuente. Añade la condición de oferta. Puedes editar y desactivar los registros; el histórico permanece.

No todas las tiendas funcionan con Jsoup. La aplicación no ejecuta JavaScript ni evade CAPTCHA, autenticaciones o restricciones. Una ficha con varias variantes o precios ambiguos falla en vez de guardar un valor arbitrario. Para probar una ficha sin base de datos:

```powershell
mvn.cmd -B -ntp compile exec:java '-Dexec.mainClass=pe.edu.monitoreo.Probe'
```

Sin argumentos prueba la ficha precargada de Plaza Vea. Para otras URLs, consulta `docs/scraping.md`.

## Monitoreo programado

Marca **Programar** en cada vínculo y activa **Monitoreo programado durante esta sesión** en Inicio. La aplicación revisa pendientes cada minuto y utiliza el intervalo de la fuente (60 min por defecto, mínimo 15 min). Requiere aplicación abierta y sesión activa. El trabajo se ejecuta en un único hilo de fondo. Las ejecuciones manuales son explícitas y adelantan la siguiente ejecución programada.

## Pruebas

```powershell
.\scripts\Test.ps1             # unitarias e integración PostgreSQL
.\scripts\Test.ps1 -Live       # además consulta las dos fichas reales
.\scripts\Test.ps1 -Ui         # además abre temporalmente la interfaz para probarla
```

Cada prueba de integración usa un esquema nuevo y lo elimina al terminar; no borra los datos de usuario. Evidencias y reportes quedan en `artifacts/` y `target/surefire-reports/`. Las pruebas vivas dependen de disponibilidad y estructura externa. El detalle de CP-01 a CP-08 y sus límites está en `docs/pruebas.md`.

## Base de datos y respaldo

El arranque aplica el esquema y la ampliación v2 transaccionalmente. `database/schema_monitoreo_precios_final.sql` contiene el SQL consolidado; `database/datos_prueba.sql` agrega ejemplos sin crear cuentas.

```powershell
.\scripts\Backup.ps1
.\scripts\Restore.ps1 -BackupPath '.local/backup-FECHA.dump' -NewDatabase restauracion_prueba
.\scripts\Stop-Database.ps1
```

El respaldo utiliza la conexión del mismo archivo. La restauración crea una base nueva si el usuario tiene permiso CREATEDB y conserva la original. Si no tiene ese permiso, prepara una base vacía con un administrador y agrega `-DatabaseAlreadyCreated`. Consulta `docs/instalacion.md` para los permisos y versiones.

## Documentación

- `docs/arquitectura.md`: responsabilidades y recorrido de los datos.
- `docs/base-datos.md`: tablas, restricciones, migración y respaldo.
- `docs/scraping.md`: extractores, límites, selectores y mantenimiento.
- `docs/reportes.md`: filtros, fórmulas y exportaciones.
- `docs/pruebas.md`: criterios y evidencia ejecutada.
- `docs/instalacion.md`: entorno y arranque.

La utilidad calculada es bruta y unitaria, bajo los costos informados. No incorpora costos adicionales ni ajustes tributarios. El sistema conserva la decisión comercial en el usuario.
