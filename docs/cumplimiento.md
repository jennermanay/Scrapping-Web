# Correspondencia con el informe

| Requisito | Implementación |
|---|---|
| RF-01 Autenticar | Primer administrador, ingreso, cierre, PBKDF2, roles y control en Store |
| RF-02 Gestionar productos | Alta, consulta, edición y desactivación en Productos |
| RF-03 Registrar atributos | Marca, modelo/variedad, presentación, capacidad y palabras clave |
| RF-04 Datos comerciales | Costo y precio propio positivos, BigDecimal y CHECK SQL |
| RF-05 Gestionar fuentes | Dominio, selectores/atributos, estrategia, intervalo, vigencia y estado |
| RF-06 Scraping | JavaFX Task, ejecutor único, vista previa y programación durante sesión |
| RF-07 Guardar resultados | Nombre, precio, moneda, URL final, fuente, producto, fecha y evidencia |
| RF-08 Homologación | Confirmar o rechazar con usuario autenticado y observación |
| RF-09 Comparación | Requiere homologación confirmada, vigencia y catálogo activo |
| RF-10 Utilidad | Utilidad bruta unitaria actual y simulada con BigDecimal |
| RF-11 Margen | Márgenes actual/simulado sobre venta, dos decimales |
| RF-12 Alertas | Precio inferior o posible pérdida; únicas por comparación |
| RF-13 Atención | Estado, responsable, fecha, observación y auditoría de cambios |
| RF-14 Historial | Capturas y variación por producto, fuente y fechas |
| RF-15 Reportes | Comparaciones/márgenes, alertas, historial, ejecuciones, homologaciones y exportaciones; PDF/CSV |
| RF-16 Errores | Ejecuciones ERROR, causa y logs rotativos sin borrar precios previos |

Las reglas RN-01 a RN-06 se implementan en homologación y comparación: no se comparan pendientes/rechazados, se documentan atributos, se alerta por precio menor, se conserva la decisión humana, se advierte pérdida y se guarda snapshot. Los atributos y condiciones comerciales requieren revisión humana; no se declara una equivalencia automática por similitud del nombre.

## Ajustes que deben reflejarse en la documentación técnica

La presentación debe asignar la revisión comercial al analista y mostrar que los ajustes de esquema ya se implementaron. En el código se utilizan tablas físicas fuente/ejecucion/resultado/comparacion con vistas compatibles fuente_scraping/scraping_ejecucion/scraping_detalle/comparacion_precio. Los selectores son columnas explícitas; JSONB se usa en parámetros de reporte. El DAO y las transacciones están concentrados en Store en este sistema.

El informe debe distinguir el cronograma y diseño propuestos de la evidencia real de `validacion.md`. La programación depende de una sesión de escritorio abierta. La compatibilidad real se comprobó con las dos fichas indicadas y no con todos los catálogos de retail.
