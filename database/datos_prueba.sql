-- Ejemplos de prueba; no crea usuarios ni contraseñas.
-- Costos/precios propios ficticios. Precios externos se obtienen al extraer.
BEGIN;
INSERT INTO categoria(nombre) VALUES ('Lácteos') ON CONFLICT DO NOTHING;
INSERT INTO producto(codigo,nombre,atributos,marca,modelo,presentacion,capacidad,categoria_id,costo,precio)
 SELECT 'GLORIA-946','Leche Entera UHT GLORIA Caja 946ml','GLORIA · Entera UHT · Caja · 946 ml','GLORIA','Entera UHT','Caja','946 ml',id,4.00,7.00 FROM categoria WHERE nombre='Lácteos'
 ON CONFLICT(codigo) DO NOTHING;
INSERT INTO palabra_clave(producto_id,termino) SELECT id,'gloria' FROM producto WHERE codigo='GLORIA-946' ON CONFLICT DO NOTHING;
INSERT INTO fuente(nombre,url,selector_titulo,selector_precio,estrategia)
 VALUES ('Plaza Vea · HTML VTEX','https://www.plazavea.com.pe/leche-gloria-entera-uhtcon-vitamina-a-cy-d-caja-1l/p','h1','.precio','VTEX') ON CONFLICT(url) DO NOTHING;
INSERT INTO fuente(nombre,url,selector_titulo,selector_precio,estrategia)
 VALUES ('Makro · HTML VTEX','https://www.makro.plazavea.com.pe/leche-gloria-entera-uhtcon-vitamina-a-cy-d-caja-1l/p','h1','.precio','VTEX') ON CONFLICT(url) DO NOTHING;
INSERT INTO producto_fuente(producto_id,fuente_id,url,condicion)
 SELECT p.id,f.id,f.url,'Precio web público; verificar ubicación, canal y condiciones. Costo y precio propio de prueba.'
 FROM producto p CROSS JOIN fuente f WHERE p.codigo='GLORIA-946' AND f.nombre IN ('Plaza Vea · HTML VTEX','Makro · HTML VTEX')
 ON CONFLICT(producto_id,fuente_id) DO NOTHING;
COMMIT;
