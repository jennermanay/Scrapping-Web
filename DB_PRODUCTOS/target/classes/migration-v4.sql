ALTER TABLE producto_fuente ADD COLUMN IF NOT EXISTS es_referencia BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE IF NOT EXISTS producto_referencia (
 id BIGSERIAL PRIMARY KEY,producto_id BIGINT NOT NULL REFERENCES producto(id),fuente_id BIGINT NOT NULL REFERENCES fuente(id),
 usuario_id BIGINT NOT NULL REFERENCES usuario(id),url TEXT NOT NULL,titulo TEXT NOT NULL,precio NUMERIC(12,2) NOT NULL CHECK(precio>0),
 capturado_en TIMESTAMPTZ NOT NULL,evidencia TEXT NOT NULL,atributos JSONB NOT NULL,
 UNIQUE(producto_id,url,capturado_en)
);
CREATE INDEX IF NOT EXISTS producto_referencia_producto_idx ON producto_referencia(producto_id,capturado_en DESC);
