CREATE TABLE IF NOT EXISTS rol_definicion(codigo TEXT PRIMARY KEY,nombre TEXT NOT NULL,activo BOOLEAN NOT NULL DEFAULT TRUE);
CREATE TABLE IF NOT EXISTS rol_permiso(rol TEXT NOT NULL REFERENCES rol_definicion(codigo),modulo TEXT NOT NULL CHECK(modulo IN ('INICIO','PRODUCTOS','MONITOREO','FUENTES','FICHAS','EXTRACCION','ALERTAS','REPORTES','USUARIOS','ROLES')),PRIMARY KEY(rol,modulo));
WITH nuevos AS (
 INSERT INTO rol_definicion(codigo,nombre) VALUES ('ADMIN','Administrador'),('ANALISTA','Analista') ON CONFLICT DO NOTHING RETURNING codigo
)
INSERT INTO rol_permiso(rol,modulo)
SELECT n.codigo,m FROM nuevos n CROSS JOIN unnest(ARRAY['INICIO','PRODUCTOS','MONITOREO','FUENTES','FICHAS','EXTRACCION','ALERTAS','REPORTES','USUARIOS','ROLES']) m
WHERE n.codigo='ADMIN' OR m IN ('PRODUCTOS','EXTRACCION','ALERTAS');
ALTER TABLE usuario DROP CONSTRAINT IF EXISTS usuario_rol_check;
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='usuario_rol_fk' AND conrelid='usuario'::regclass) THEN ALTER TABLE usuario ADD CONSTRAINT usuario_rol_fk FOREIGN KEY(rol) REFERENCES rol_definicion(codigo);END IF;
END $$;
CREATE TABLE IF NOT EXISTS producto_monitoreo(producto_id BIGINT PRIMARY KEY REFERENCES producto(id),intervalo_min INTEGER NOT NULL DEFAULT 60 CHECK(intervalo_min BETWEEN 1 AND 10080),programado BOOLEAN NOT NULL DEFAULT FALSE);
INSERT INTO producto_monitoreo(producto_id,intervalo_min,programado)
SELECT pf.producto_id,MIN(f.intervalo),BOOL_OR(pf.programado) FROM producto_fuente pf JOIN fuente f ON f.id=pf.fuente_id WHERE NOT pf.es_referencia GROUP BY pf.producto_id ON CONFLICT DO NOTHING;
