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
