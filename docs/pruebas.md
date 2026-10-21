# Pruebas y evidencia

Las pruebas unitarias verifican cálculo, importes, extractores, hashes y archivos. Las pruebas de integración ejecutan transacciones reales sobre PostgreSQL en un esquema UUID independiente. Al terminar eliminan exclusivamente ese esquema. La prueba de interfaz usa otra base lógica aislada mediante esquema y no conserva una cuenta de demostración en la base del usuario.

## Ejecución

`scripts/Test.ps1` habilita integración. `-Live` añade consultas a las fichas reales precargadas. `-Ui` abre una ventana temporal JavaFX y comprueba el flujo mediante controles reales. Las opciones pueden combinarse. `mvn test` sin variables ejecuta las pruebas unitarias y marca las pruebas dependientes del entorno como omitidas.

Los XML y resúmenes de JUnit quedan en target/surefire-reports. Los archivos artifacts/prueba-real.csv y artifacts/prueba-real.pdf contienen la evidencia de comparación con precios vivos y con costos/precios propios de prueba. La prueba visual guarda capturas de pantalla de JavaFX.

## Casos del informe

| Caso | Verificación automatizada |
|---|---|
| CP-01 | Producto válido persistido con costo 80 y precio propio 120 |
| CP-02 | Costo negativo rechazado antes de escribir |
| CP-03 | Externo 105, utilidad simulada 25, margen 23.81 %, alerta inferior |
| CP-04 | Externo 130, sin alerta inferior nueva |
| CP-05 | Externo 70, señal de pérdida |
| CP-06 | Pendiente no comparable; rechazo por 500 ml frente a 1 L impide comparar |
| CP-07 | Timeout controlado registra ERROR y conserva histórico |
| CP-08 | S/ 1,299.90 normalizado a 1299.90; formatos ambiguos rechazados |

También se comprueban snapshot tras edición de catálogo, duplicados, vigencia, roles, atención auditada, filtros, exportaciones, migración idempotente, segunda cuenta inicial bloqueada y vínculo desactivado. Los fallos de red se simulan mediante IExtractorFuente sin esperar diez segundos por intento.

## Alcance de la evidencia

Una prueba viva demuestra el acceso y extracción en la fecha de ejecución, sin garantizar que una tienda mantenga su HTML. La homologación de la prueba real valida el ejemplo GLORIA Caja 946ml por su título; en uso normal la realiza la persona autenticada tras revisar todos los atributos y condiciones.

La prueba JavaFX realiza creación de administrador, acceso, carga de catálogo ficticio, extracción, homologación, comparación y consulta del reporte. No sustituye todas las pruebas manuales de accesibilidad ni acredita un tiempo máximo de tres segundos bajo cualquier volumen. La programación y los filtros deben probarse en las condiciones del laboratorio y documentarse con el volumen de datos usado.

Resultados concretos, fecha y archivos de la última ejecución se documentan en `docs/validacion.md` después de ejecutar la suite.
