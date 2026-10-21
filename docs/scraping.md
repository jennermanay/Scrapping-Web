# Extracción de páginas públicas

La unidad de consulta es una ficha de producto. No se recorre automáticamente un catálogo ni se realizan compras. Cada fuente define una estrategia, selectores, intervalo y vigencia. Cada vínculo define la URL específica, condición comercial y programación.

## Fichas reales verificadas

El 21 de octubre de 2026 se obtuvo HTTP 200 y un objeto público skuJson desde las fichas de GLORIA Entera UHT Caja 946ml en los dominios `www.plazavea.com.pe` y `www.makro.plazavea.com.pe`. Ambos pertenecen a la misma plataforma comercial; la muestra no acredita monitoreo de comercios independientes. El precio vivo y fecha se guardan en los reportes de la prueba, no se fijan en código.

La URL consultada de Metro `https://www.metro.pe/leche-entera-uht-gloria-caja-1l/p` devolvió 404 durante la revisión y no se precarga como fuente funcional. Un resultado indexado en buscadores no prueba disponibilidad actual.

## Estrategias

CSS exige exactamente una coincidencia de título y una de precio. Se usa `element.text()` por defecto; si se especifica atributo se usa `element.attr(nombre)`. Para metadatos habituales puede utilizarse título `meta[property=og:title]` con atributo `content` y precio `meta[itemprop=price]` con atributo `content`, siempre después de verificar la página concreta.

VTEX analiza JSON incrustado en el HTML dentro de `var skuJson_0 = {...};`. No ejecuta JavaScript. Exige un solo objeto de producto, una variante y disponibilidad de producto y SKU. Convierte `bestPrice` de céntimos a PEN y guarda el SKU, canal y valor original en la evidencia. Una variante múltiple se rechaza para no elegir un precio arbitrario.

JSON_LD busca un único Product en scripts application/ld+json, incluso en @graph. Requiere una oferta individual con price y priceCurrency PEN; rechaza múltiples ofertas, moneda distinta y ausencia de precio. No toma lowPrice de ofertas agregadas como precio individual.

DEMO procesa HTML ficticio con `demo://producto` y nunca realiza una consulta externa.

## Formatos y condiciones

Se aceptan importes como `S/ 1,299.90`, `PEN 1299.90`, `5.3` y `5`. Se rechazan cero, negativos, rangos y formatos ambiguos como `1.299,90`. Una página con moneda no declarada mediante CSS debe revisarse para confirmar que el valor corresponde a PEN.

La condición comercial es un dato declarado por quien configura la ficha y debe verificarse en cada homologación. El sistema no deduce automáticamente restricciones por tarjeta, membresía, ubicación o cantidad mínima. El costo y precio propio de los ejemplos son de prueba y no provienen de sistemas de Plaza Vea.

## Errores y mantenimiento

Timeout: 10 s por intento; máximo un reintento para IOException de red o HTTP 5xx. Los HTTP 4xx y las restricciones reconocibles no se reintentan. No se evaden autenticaciones, CAPTCHA ni bloqueos. Ante cambios de HTML, desactiva la fuente, revisa selectores o estrategia y utiliza vista previa antes de reactivar.

Cada captura conserva fecha, fuente, producto, URL final y evidencia. Las fallas conservan ejecución, usuario y causa. Los archivos `logs/scraping-N.log` rotan con límite de tamaño. No se guarda el HTML completo de las páginas en la base.

Para probar otro dominio sin guardar registros, ajusta JAVA_HOME a JDK 21 y ejecuta:

```powershell
mvn.cmd -B -ntp compile exec:java '-Dexec.mainClass=pe.edu.monitoreo.Probe' '-Dexec.args=https://tienda.example/producto JSON_LD'
```

Reemplaza la URL de ejemplo. Para CSS los siguientes argumentos son selector de título y selector de precio; configura atributos desde la interfaz cuando corresponda.
