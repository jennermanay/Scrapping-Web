# Base de datos

El esquema físico se implementa en PostgreSQL. Se conservan los nombres de las cuatro tablas de la primera versión para poder ampliar una instalación existente sin borrar registros.

| Entidad del informe | Implementación |
|---|---|
| usuario | Cuenta, hash, rol ADMIN/ANALISTA y estado activo |
| categoria | Categorías únicas |
| producto | Código único, atributos estructurados, costo, precio propio y estado |
| palabra_clave | Términos por producto, sin duplicados |
| fuente_scraping | Vista de compatibilidad sobre la tabla `fuente` |
| producto_fuente | Par producto/fuente único, URL, condición y programación |
| scraping_ejecucion | Vista sobre `ejecucion`, con usuario, vínculo, fechas y error |
| scraping_detalle | Vista sobre `resultado`, con precio, URL, evidencia y estado |
| homologacion | Decisión humana única por resultado, usuario y observación |
| comparacion_precio | Vista sobre `comparacion`, con snapshot económico y de producto |
| alerta | Tipo, estado, responsable y observación de atención |
| alerta_atencion | Auditoría de cada cambio de atención |
| historico_precio | Capturas originales con referencia única al resultado |
| reporte | Exportaciones con usuario, parámetros JSONB y ruta |

## Integridad

Se utilizan PK BIGSERIAL, FK con conservación de referencias, UNIQUE, NOT NULL y CHECK. Los importes son NUMERIC(12,2), nunca coma flotante. Los márgenes aceptan valores negativos. Las fechas usan TIMESTAMPTZ. La moneda de resultados es PEN. Los selectores se guardan en columnas explícitas en esta implementación; los parámetros de exportación usan JSONB.

No hay eliminación física de productos ni fuentes desde la interfaz: se desactivan. Cambiar costo o precio propio no altera comparaciones previas. El estado confirmado necesita registro de homologación para comparar; la operación de revisión guarda ambos cambios en una misma transacción.

El histórico contiene también candidatos no confirmados para conservar lo observado. Los reportes muestran el estado de homologación; una captura pendiente no debe interpretarse como comparación validada. La alerta tiene UNIQUE sobre comparación y la comparación tiene UNIQUE sobre resultado, evitando duplicados del mismo resultado.

## Migración

El inicio ejecuta `schema.sql`, `migration-v2.sql` y `migration-v3.sql` dentro de una transacción y con un bloqueo asesor. La ampliación agrega campos sin borrar información y rellena códigos de productos antiguos con P-ID. La tercera migración actualiza textos de ejemplos de versiones anteriores una sola vez. Los atributos estructurados de registros antiguos deben completarse antes de guardarlos desde la interfaz. Los resultados antiguos confirmados no tienen usuario verificable de homologación; deben obtenerse y revisarse nuevamente para nuevas comparaciones. Las comparaciones antiguas se conservan.

`database/schema_monitoreo_precios_final.sql` es la concatenación distribuible de los tres archivos. `scripts/Sync-Schema.ps1` la regenera. Ejecútala sobre una base revisada y respáldala antes de migrar instalaciones con datos.

## Copia y restauración

`scripts/Backup.ps1` utiliza pg_dump formato custom sobre la conexión de config/database.properties. `scripts/Restore.ps1` crea una base `restauracion_...` cuando el usuario tiene permiso CREATEDB y restaura allí con pg_restore; no limpia ni sobreescribe la base original. Si un administrador ya preparó una base vacía, se utiliza -DatabaseAlreadyCreated. El archivo de respaldo contiene cuentas con hashes y datos y debe conservarse de forma privada.

El usuario `monitoreo` de los scripts es propietario de su base y no es superusuario. El propietario del clúster se utiliza solo para crear bases y roles. En un despliegue de laboratorio se pueden separar el usuario de migración y el de ejecución; el sistema aplica migraciones al inicio y necesita permisos de DDL en su esquema.
