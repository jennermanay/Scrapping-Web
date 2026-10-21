# Reportes y cálculos

La pantalla Historial y reportes muestra consultas filtradas y permite exportarlas a CSV o PDF. Los filtros disponibles son producto, fuente, fecha inicial y fecha final. Se interpretan en America/Lima y la fecha final incluye todo ese día. El reporte de exportaciones se filtra por fechas porque cada exportación puede abarcar varios productos y fuentes.

| Reporte | Campos relevantes |
|---|---|
| Comparación competitiva y márgenes | Snapshot de producto y atributos, costo, propio, externo, diferencia, utilidades, márgenes, condición, moneda, captura, fuente y URL |
| Historial de precios capturados | Producto, fuente, precio, variación, homologación, condición, moneda y URL |
| Alertas competitivas | Comparación, tipo, estado, responsable, observación, fecha de atención y procedencia |
| Ejecuciones y errores | Fuente, producto, usuario, inicio/fin, estado, cantidad, error y URL solicitada |
| Homologaciones y evidencias | Candidato, estado, validador, observación, precio, condición, evidencia del extractor y URL |
| Registro de exportaciones | Usuario, tipo, filtros JSONB, fecha y ruta del archivo |

## Fórmulas

La diferencia es precio externo menos precio propio. Un valor negativo significa que el externo es menor.

Utilidad bruta unitaria actual = propio − costo. Margen actual = utilidad actual / propio × 100. Utilidad simulada = externo − costo. Margen simulado = utilidad simulada / externo × 100. Los cálculos usan BigDecimal y los márgenes se redondean a dos decimales HALF_UP.

Para costo 80, propio 120 y externo 105: utilidad actual 40, margen actual 33.33 %, utilidad simulada 25, margen simulado 23.81 %. Si el externo es 70 se advierte PERDIDA. Si es menor al propio pero no menor al costo se genera PRECIO_INFERIOR. La decisión comercial permanece en el usuario.

La vigencia del precio es 24 h por defecto y configurable de 1 a 168 h por fuente. Una comparación nueva requiere candidato confirmado, producto/fuente activos y captura vigente. Una comparación ya guardada no se recalcula al cambiar el catálogo.

## Exportación

Se vuelve a consultar con los filtros seleccionados al exportar; los resultados pueden incorporar datos nuevos desde la última consulta visual. El CSV es UTF-8 con BOM, comillas escapadas y protección contra fórmulas de hoja de cálculo. El PDF usa registros con sus campos, se pagina y mantiene completa la URL, aunque pueda continuar en otra línea. No se intenta comprimir todas las columnas en una página ilegible.

Tras escribir el archivo se registra la exportación. Si la base falla después de guardar el archivo, la aplicación muestra el error; revisa el archivo y vuelve a generar el reporte para tener trazabilidad. Las exportaciones no incluyen contraseñas ni hashes.
