# Monitoreo competitivo de precios

Aplicativo de escritorio en Java 21 y JavaFX. Consulta precios públicos con Jsoup, guarda homologaciones y variaciones en PostgreSQL, y exporta reportes PDF, CSV y Excel.

## Ejecutar

1. Instalar JDK 21, Maven y PostgreSQL. Crear la base `monitoreo_precios` y conceder al usuario de conexión permisos para crear su esquema y tablas.
2. Copiar `config/database.example.properties` como `config/database.properties` y completar la conexión. Este archivo privado no se sube a GitHub.
3. Ejecutar `Iniciar.cmd` o `mvn javafx:run` desde la raíz del proyecto. En el primer acceso se crea el administrador.

El aplicativo utiliza el PostgreSQL instalado. No incluye un servidor ni datos privados. Aplica su estructura SQL al iniciar.

## Monitoreo

Registrar el producto desde su URL de Plaza Vea, configurar las fuentes y homologar el candidato. La equivalencia queda guardada para las siguientes consultas. Las variaciones generan avisos de subida o bajada; la frecuencia se modifica por producto en Configurar monitoreo. La consulta automática requiere el aplicativo abierto y una sesión iniciada.

## Tienda de prueba

Node.js se usa únicamente para la tienda. Ejecutar `tienda-pruebas/Iniciar.cmd` y abrir `http://127.0.0.1:8877/admin`. Los productos se guardan en `tienda-pruebas/data/productos.json`, excluido de Git. No se requiere Node.js para el aplicativo principal.

## Verificación

`mvn test` ejecuta las pruebas unitarias. `scripts/Test.ps1` ejecuta también integración usando la conexión configurada; crea esquemas temporales independientes y los elimina al terminar. Agregar `-Ui` permite verificar las pantallas JavaFX. Las pruebas contra tiendas externas se activan con `-Live` y requieren conectividad.

Los cuatro paquetes de entrega son partes del mismo proyecto; deben integrarse conservando las rutas originales para compilar la aplicación completa.
