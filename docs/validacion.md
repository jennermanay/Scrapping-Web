# Validación ejecutada

Fecha: 21 de octubre de 2026, America/Lima. Entorno: Windows, JDK 21 de 64 bits, PostgreSQL 18 en clúster aislado del proyecto, Maven y JavaFX.

Comando final: `scripts/Test.ps1 -Ui -Live`. Resultado: **38 pruebas, 0 fallos, 0 errores, 0 omisiones**, BUILD SUCCESS. Finalizó a las 11:16:48, hora de Lima. Tiempo informado por Maven: 50.772 s. El tiempo corresponde a toda la suite, no es una medición del objetivo RNF de consultas locales.

| Grupo | Pruebas aprobadas |
|---|---:|
| ConfigTest | 3 |
| EconomicsTest | 7 |
| ExtractorTest | 8 |
| PasswordsTest | 2 |
| ReportsTest | 2 |
| StoreIntegrationTest | 15 |
| UiSmokeTest | 1 |

## Consultas reales

Producto recibido: Leche Entera UHT GLORIA Caja 946ml, SKU VTEX 3533. Se obtuvieron PEN 6.10 desde Plaza Vea y PEN 5.30 desde el canal Makro en las capturas del 21 de octubre de 2026 alrededor de las 11:16, hora de Lima. Son observaciones de ese momento y canal; pueden variar. Ambos canales pertenecen a la misma plataforma comercial y no se presentan como comercios independientes.

El ejemplo utiliza costo propio 4.00 y precio propio 7.00, ambos ficticios. Se registraron dos comparaciones confirmadas en el esquema temporal de integración. Las exportaciones persisten como evidencia en `artifacts/prueba-real.csv` y `artifacts/prueba-real.pdf`; esos resultados de prueba no se mezclan con la base de trabajo del usuario. La vista renderizada está en `artifacts/reporte-real-vista.png` y se inspeccionó para verificar legibilidad y ausencia de recortes.

## Interfaz

La prueba abrió JavaFX, creó una cuenta inicial en un esquema aislado, ingresó, cargó un catálogo ficticio, extrajo el candidato, confirmó equivalencia, comparó y consultó el reporte. Además cerró sesión, abrió la pantalla de configuración y probó el acceso PostgreSQL. Las capturas incluyen `artifacts/interfaz-productos.png`, `artifacts/interfaz-reportes.png` y `artifacts/interfaz-conexion.png`. La cuenta de prueba se eliminó con su esquema; no hay credenciales de demostración en la base del usuario.

Se verificó por psql el archivo `config/database.properties`: base monitoreo_precios, usuario monitoreo, PostgreSQL 18.6 de 64 bits. La aplicación usa esa conexión por JDBC, sin sustituciones silenciosas de credenciales ni creación automática de bases.

## Respaldo

Se ejecutó `Backup.ps1` sobre la base local con catálogos preparados y se restauró mediante `Restore.ps1` en `restauracion_verificacion`. Ambos procesos terminaron con código 0. La base original `monitoreo_precios` se conservó. El respaldo de comprobación está en `.local/verificacion.dump` y no debe publicarse.

## Límites de la validación

No se acreditan tiempos de mejora empresarial, compatibilidad con todas las tiendas, funcionamiento con la aplicación cerrada ni operación productiva. No se realizaron pruebas de carga masivas. Los ocho casos de prueba se verifican por reglas y persistencia; el recorrido de interfaz automatizado cubre una ruta principal, no cada combinación de formulario.
