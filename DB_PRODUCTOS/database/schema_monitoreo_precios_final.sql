-- Esquema consolidado; regenerar con scripts/Sync-Schema.ps1
BEGIN;
CREATE TABLE IF NOT EXISTS producto (
 id BIGSERIAL PRIMARY KEY, nombre TEXT NOT NULL CHECK(length(trim(nombre))>0),
 atributos TEXT NOT NULL CHECK(length(trim(atributos))>0),
 costo NUMERIC(12,2) NOT NULL CHECK(costo>0), precio NUMERIC(12,2) NOT NULL CHECK(precio>0)
);
CREATE TABLE IF NOT EXISTS fuente (
 id BIGSERIAL PRIMARY KEY, nombre TEXT NOT NULL, url TEXT NOT NULL UNIQUE,
 selector_titulo TEXT NOT NULL, selector_precio TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS ejecucion (
 id BIGSERIAL PRIMARY KEY, producto_id BIGINT NOT NULL REFERENCES producto(id), fuente_id BIGINT NOT NULL REFERENCES fuente(id),
 inicio TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, fin TIMESTAMPTZ,
 estado TEXT NOT NULL DEFAULT 'EN_CURSO' CHECK(estado IN ('EN_CURSO','OK','ERROR')), error TEXT
);
CREATE TABLE IF NOT EXISTS resultado (
 id BIGSERIAL PRIMARY KEY, ejecucion_id BIGINT NOT NULL UNIQUE REFERENCES ejecucion(id),
 titulo TEXT NOT NULL, precio NUMERIC(12,2) NOT NULL CHECK(precio>0), url TEXT NOT NULL,
 condicion TEXT NOT NULL, fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 estado TEXT NOT NULL DEFAULT 'PENDIENTE' CHECK(estado IN ('PENDIENTE','CONFIRMADO','RECHAZADO')),
 responsable TEXT, observacion TEXT, validado_en TIMESTAMPTZ,
 CHECK(estado='PENDIENTE' OR (length(trim(responsable))>0 AND length(trim(observacion))>0 AND responsable IS NOT NULL AND observacion IS NOT NULL AND validado_en IS NOT NULL))
);
CREATE TABLE IF NOT EXISTS comparacion (
 id BIGSERIAL PRIMARY KEY, resultado_id BIGINT NOT NULL UNIQUE REFERENCES resultado(id),
 fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 costo NUMERIC(12,2) NOT NULL CHECK(costo>0), propio NUMERIC(12,2) NOT NULL CHECK(propio>0), externo NUMERIC(12,2) NOT NULL CHECK(externo>0),
 diferencia NUMERIC(12,2) NOT NULL, utilidad_actual NUMERIC(12,2) NOT NULL, utilidad_simulada NUMERIC(12,2) NOT NULL,
 margen_actual NUMERIC(18,2) NOT NULL, margen_simulado NUMERIC(18,2) NOT NULL,
 alerta TEXT NOT NULL CHECK(alerta IN ('PERDIDA','PRECIO_INFERIOR','SIN_ALERTA'))
);
CREATE INDEX IF NOT EXISTS ejecucion_producto_idx ON ejecucion(producto_id);

CREATE TABLE IF NOT EXISTS usuario (
 id BIGSERIAL PRIMARY KEY, correo TEXT NOT NULL UNIQUE, nombre TEXT NOT NULL,
 password_hash TEXT NOT NULL, rol TEXT NOT NULL CHECK(rol IN ('ADMIN','ANALISTA')), activo BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS categoria (id BIGSERIAL PRIMARY KEY,nombre TEXT NOT NULL UNIQUE);
INSERT INTO categoria(nombre) VALUES ('General') ON CONFLICT DO NOTHING;
ALTER TABLE producto ADD COLUMN IF NOT EXISTS codigo TEXT;
UPDATE producto SET codigo='P-'||id WHERE codigo IS NULL;
ALTER TABLE producto ALTER COLUMN codigo SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS producto_codigo_idx ON producto(codigo);
ALTER TABLE producto ADD COLUMN IF NOT EXISTS marca TEXT NOT NULL DEFAULT '';
ALTER TABLE producto ADD COLUMN IF NOT EXISTS modelo TEXT NOT NULL DEFAULT '';
ALTER TABLE producto ADD COLUMN IF NOT EXISTS presentacion TEXT NOT NULL DEFAULT '';
ALTER TABLE producto ADD COLUMN IF NOT EXISTS capacidad TEXT NOT NULL DEFAULT '';
ALTER TABLE producto ADD COLUMN IF NOT EXISTS activo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE producto ADD COLUMN IF NOT EXISTS categoria_id BIGINT REFERENCES categoria(id);
CREATE TABLE IF NOT EXISTS palabra_clave (producto_id BIGINT NOT NULL REFERENCES producto(id),termino TEXT NOT NULL,PRIMARY KEY(producto_id,termino));
ALTER TABLE fuente ADD COLUMN IF NOT EXISTS estrategia TEXT NOT NULL DEFAULT 'CSS' CHECK(estrategia IN ('CSS','JSON_LD','VTEX','DEMO'));
ALTER TABLE fuente ADD COLUMN IF NOT EXISTS intervalo INTEGER NOT NULL DEFAULT 60 CHECK(intervalo>=15);
ALTER TABLE fuente ADD COLUMN IF NOT EXISTS vigencia INTEGER NOT NULL DEFAULT 24 CHECK(vigencia BETWEEN 1 AND 168);
ALTER TABLE fuente ADD COLUMN IF NOT EXISTS activo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE fuente ADD COLUMN IF NOT EXISTS atributo_titulo TEXT NOT NULL DEFAULT '';
ALTER TABLE fuente ADD COLUMN IF NOT EXISTS atributo_precio TEXT NOT NULL DEFAULT '';
ALTER TABLE fuente ADD COLUMN IF NOT EXISTS revisado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE TABLE IF NOT EXISTS producto_fuente (
 id BIGSERIAL PRIMARY KEY,producto_id BIGINT NOT NULL REFERENCES producto(id),fuente_id BIGINT NOT NULL REFERENCES fuente(id),
 url TEXT NOT NULL,condicion TEXT NOT NULL,programado BOOLEAN NOT NULL DEFAULT FALSE,activo BOOLEAN NOT NULL DEFAULT TRUE,
 proxima_ejecucion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE(producto_id,fuente_id)
);
ALTER TABLE ejecucion ADD COLUMN IF NOT EXISTS usuario_id BIGINT REFERENCES usuario(id);
ALTER TABLE ejecucion ADD COLUMN IF NOT EXISTS vinculo_id BIGINT REFERENCES producto_fuente(id);
ALTER TABLE ejecucion ADD COLUMN IF NOT EXISTS url TEXT;
ALTER TABLE resultado ADD COLUMN IF NOT EXISTS evidencia TEXT NOT NULL DEFAULT '';
ALTER TABLE resultado ADD COLUMN IF NOT EXISTS moneda TEXT NOT NULL DEFAULT 'PEN' CHECK(moneda='PEN');
CREATE TABLE IF NOT EXISTS homologacion (
 id BIGSERIAL PRIMARY KEY,resultado_id BIGINT NOT NULL UNIQUE REFERENCES resultado(id),usuario_id BIGINT NOT NULL REFERENCES usuario(id),
 estado TEXT NOT NULL CHECK(estado IN ('CONFIRMADO','RECHAZADO')),observacion TEXT NOT NULL CHECK(length(trim(observacion))>0),fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE comparacion ADD COLUMN IF NOT EXISTS usuario_id BIGINT REFERENCES usuario(id);
ALTER TABLE comparacion ADD COLUMN IF NOT EXISTS producto_snapshot TEXT NOT NULL DEFAULT '';
ALTER TABLE comparacion ADD COLUMN IF NOT EXISTS atributos_snapshot TEXT NOT NULL DEFAULT '';
CREATE TABLE IF NOT EXISTS alerta (
 id BIGSERIAL PRIMARY KEY,comparacion_id BIGINT NOT NULL UNIQUE REFERENCES comparacion(id),tipo TEXT NOT NULL CHECK(tipo IN ('PRECIO_INFERIOR','PERDIDA')),
 estado TEXT NOT NULL DEFAULT 'PENDIENTE' CHECK(estado IN ('PENDIENTE','EN_REVISION','ATENDIDA','DESCARTADA')),
 responsable_id BIGINT REFERENCES usuario(id),observacion TEXT NOT NULL DEFAULT '',atendida_en TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS alerta_atencion (
 id BIGSERIAL PRIMARY KEY,alerta_id BIGINT NOT NULL REFERENCES alerta(id),usuario_id BIGINT NOT NULL REFERENCES usuario(id),
 estado TEXT NOT NULL,observacion TEXT NOT NULL,fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS historico_precio (
 id BIGSERIAL PRIMARY KEY,resultado_id BIGINT NOT NULL UNIQUE REFERENCES resultado(id),producto_id BIGINT NOT NULL REFERENCES producto(id),
 fuente_id BIGINT NOT NULL REFERENCES fuente(id),precio NUMERIC(12,2) NOT NULL CHECK(precio>0),fecha TIMESTAMPTZ NOT NULL,url TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS reporte (
 id BIGSERIAL PRIMARY KEY,usuario_id BIGINT NOT NULL REFERENCES usuario(id),tipo TEXT NOT NULL,parametros JSONB NOT NULL,
 archivo TEXT NOT NULL,fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS resultado_fecha_idx ON resultado(fecha);
CREATE INDEX IF NOT EXISTS historico_filtros_idx ON historico_precio(producto_id,fuente_id,fecha);
CREATE INDEX IF NOT EXISTS programados_idx ON producto_fuente(proxima_ejecucion) WHERE programado AND activo;
INSERT INTO historico_precio(resultado_id,producto_id,fuente_id,precio,fecha,url)
 SELECT r.id,e.producto_id,e.fuente_id,r.precio,r.fecha,r.url FROM resultado r JOIN ejecucion e ON e.id=r.ejecucion_id ON CONFLICT DO NOTHING;
INSERT INTO alerta(comparacion_id,tipo) SELECT id,alerta FROM comparacion WHERE alerta<>'SIN_ALERTA' ON CONFLICT DO NOTHING;
CREATE OR REPLACE VIEW fuente_scraping AS SELECT * FROM fuente;
CREATE OR REPLACE VIEW scraping_ejecucion AS SELECT * FROM ejecucion;
CREATE OR REPLACE VIEW scraping_detalle AS SELECT * FROM resultado;
CREATE OR REPLACE VIEW comparacion_precio AS SELECT * FROM comparacion;

CREATE TABLE IF NOT EXISTS schema_migration(version INTEGER PRIMARY KEY,aplicado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
DO $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM schema_migration WHERE version=3) THEN
  UPDATE producto_fuente SET condicion=replace(replace(condicion,'Ficticio académico','Ejemplo ficticio'),'ejemplos académicos','ejemplos de prueba')
   WHERE condicion LIKE '%Ficticio académico%' OR condicion LIKE '%ejemplos académicos%';
  UPDATE producto_fuente SET condicion=replace(condicion,'precio propio académicos','precio propio de ejemplo') WHERE condicion LIKE '%precio propio académicos%';
  UPDATE resultado SET condicion=replace(replace(condicion,'Ficticio académico','Ejemplo ficticio'),'ejemplos académicos','ejemplos de prueba')
   WHERE condicion LIKE '%Ficticio académico%' OR condicion LIKE '%ejemplos académicos%';
  INSERT INTO schema_migration(version) VALUES (3);
 END IF;
END $$;

ALTER TABLE producto_fuente ADD COLUMN IF NOT EXISTS es_referencia BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE IF NOT EXISTS producto_referencia (
 id BIGSERIAL PRIMARY KEY,producto_id BIGINT NOT NULL REFERENCES producto(id),fuente_id BIGINT NOT NULL REFERENCES fuente(id),
 usuario_id BIGINT NOT NULL REFERENCES usuario(id),url TEXT NOT NULL,titulo TEXT NOT NULL,precio NUMERIC(12,2) NOT NULL CHECK(precio>0),
 capturado_en TIMESTAMPTZ NOT NULL,evidencia TEXT NOT NULL,atributos JSONB NOT NULL,
 UNIQUE(producto_id,url,capturado_en)
);
CREATE INDEX IF NOT EXISTS producto_referencia_producto_idx ON producto_referencia(producto_id,capturado_en DESC);

ALTER TABLE usuario ADD COLUMN IF NOT EXISTS nombre_usuario TEXT;
ALTER TABLE usuario ALTER COLUMN correo DROP NOT NULL;
DO $$
DECLARE account RECORD; candidate TEXT;
BEGIN
 FOR account IN SELECT id,correo FROM usuario WHERE nombre_usuario IS NULL ORDER BY id LOOP
  candidate:=left(regexp_replace(lower(split_part(COALESCE(account.correo,''),'@',1)),'[^a-z0-9._-]','','g'),40);
  IF length(candidate)<3 OR candidate !~ '^[a-z0-9]' THEN candidate:='usuario_'||account.id; END IF;
  IF EXISTS(SELECT 1 FROM usuario WHERE lower(nombre_usuario)=candidate) THEN candidate:=left(candidate,20)||'_'||account.id; END IF;
  UPDATE usuario SET nombre_usuario=candidate WHERE id=account.id;
 END LOOP;
END $$;
ALTER TABLE usuario ALTER COLUMN nombre_usuario SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS usuario_nombre_usuario_idx ON usuario(lower(nombre_usuario));
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='usuario'::regclass AND conname='usuario_nombre_usuario_formato') THEN
  ALTER TABLE usuario ADD CONSTRAINT usuario_nombre_usuario_formato CHECK(nombre_usuario ~ '^[a-z0-9][a-z0-9._-]{2,39}$');
 END IF;
END $$;

ALTER TABLE fuente DROP CONSTRAINT IF EXISTS fuente_estrategia_check;
ALTER TABLE fuente ADD CONSTRAINT fuente_estrategia_check CHECK(estrategia IN ('CSS','JSON_LD','VTEX','DEMO','AUTO'));
ALTER TABLE producto_fuente ADD COLUMN IF NOT EXISTS busqueda_automatica BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE resultado DROP CONSTRAINT IF EXISTS resultado_ejecucion_id_key;
CREATE UNIQUE INDEX IF NOT EXISTS resultado_ejecucion_url_idx ON resultado(ejecucion_id,url);
ALTER TABLE ejecucion DROP CONSTRAINT IF EXISTS ejecucion_estado_check;
ALTER TABLE ejecucion ADD CONSTRAINT ejecucion_estado_check CHECK(estado IN ('EN_CURSO','OK','ERROR','SIN_RESULTADOS'));
ALTER TABLE ejecucion ADD COLUMN IF NOT EXISTS resumen_busqueda JSONB;

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

CREATE TABLE IF NOT EXISTS alerta_decision_precio (
 id BIGSERIAL PRIMARY KEY,alerta_id BIGINT NOT NULL UNIQUE REFERENCES alerta(id),producto_id BIGINT NOT NULL REFERENCES producto(id),usuario_id BIGINT NOT NULL REFERENCES usuario(id),
 accion TEXT NOT NULL CHECK(accion IN ('KEEP','MATCH','LOWER')),precio_anterior NUMERIC(12,2) NOT NULL CHECK(precio_anterior>0),precio_nuevo NUMERIC(12,2) NOT NULL CHECK(precio_nuevo>0),
 costo NUMERIC(12,2) NOT NULL CHECK(costo>0),utilidad NUMERIC(12,2) NOT NULL,margen NUMERIC(18,2) NOT NULL,observacion TEXT NOT NULL DEFAULT '',fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS producto_homologado (
 id BIGSERIAL PRIMARY KEY,producto_id BIGINT NOT NULL REFERENCES producto(id),fuente_id BIGINT NOT NULL REFERENCES fuente(id),
 url TEXT NOT NULL,titulo TEXT NOT NULL,atributos_propios TEXT NOT NULL,condicion TEXT NOT NULL,
 usuario_id BIGINT NOT NULL REFERENCES usuario(id),homologado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 ultimo_resultado_id BIGINT NOT NULL REFERENCES resultado(id),ultimo_precio NUMERIC(12,2) NOT NULL CHECK(ultimo_precio>0),
 verificado_en TIMESTAMPTZ NOT NULL,estado TEXT NOT NULL DEFAULT 'ACTIVO' CHECK(estado IN ('ACTIVO','REVISAR')),motivo TEXT NOT NULL DEFAULT '',
 UNIQUE(producto_id,fuente_id,url)
);
ALTER TABLE comparacion DROP CONSTRAINT IF EXISTS comparacion_alerta_check;
ALTER TABLE comparacion ADD CONSTRAINT comparacion_alerta_check CHECK(alerta IN ('PERDIDA','PRECIO_INFERIOR','SIN_ALERTA','SUBIDA_PRECIO','BAJADA_PRECIO'));
ALTER TABLE alerta DROP CONSTRAINT IF EXISTS alerta_tipo_check;
ALTER TABLE alerta ADD CONSTRAINT alerta_tipo_check CHECK(tipo IN ('PERDIDA','PRECIO_INFERIOR','SUBIDA_PRECIO','BAJADA_PRECIO'));
ALTER TABLE comparacion ADD COLUMN IF NOT EXISTS precio_anterior NUMERIC(12,2);
ALTER TABLE comparacion ADD COLUMN IF NOT EXISTS variacion NUMERIC(12,2);
INSERT INTO producto_homologado(producto_id,fuente_id,url,titulo,atributos_propios,condicion,usuario_id,homologado_en,ultimo_resultado_id,ultimo_precio,verificado_en)
 SELECT DISTINCT ON(e.producto_id,e.fuente_id,r.url) e.producto_id,e.fuente_id,r.url,r.titulo,p.atributos,r.condicion,h.usuario_id,h.fecha,r.id,r.precio,r.fecha
 FROM homologacion h JOIN resultado r ON r.id=h.resultado_id JOIN ejecucion e ON e.id=r.ejecucion_id JOIN producto p ON p.id=e.producto_id LEFT JOIN producto_fuente pf ON pf.id=e.vinculo_id
 WHERE h.estado='CONFIRMADO' AND r.estado='CONFIRMADO' AND NOT COALESCE(pf.es_referencia,FALSE)
 ORDER BY e.producto_id,e.fuente_id,r.url,r.fecha DESC,r.id DESC ON CONFLICT(producto_id,fuente_id,url) DO NOTHING;
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM schema_migration WHERE version=9) THEN
  UPDATE producto_fuente pf SET programado=TRUE WHERE NOT pf.es_referencia AND EXISTS(SELECT 1 FROM producto_homologado ph WHERE ph.producto_id=pf.producto_id AND ph.fuente_id=pf.fuente_id);
  UPDATE producto_monitoreo pm SET programado=TRUE WHERE EXISTS(SELECT 1 FROM producto_homologado ph WHERE ph.producto_id=pm.producto_id);
 END IF;
END $$;
INSERT INTO schema_migration(version) VALUES(9) ON CONFLICT DO NOTHING;

COMMIT;
