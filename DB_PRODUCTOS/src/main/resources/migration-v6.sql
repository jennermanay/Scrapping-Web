ALTER TABLE fuente DROP CONSTRAINT IF EXISTS fuente_estrategia_check;
ALTER TABLE fuente ADD CONSTRAINT fuente_estrategia_check CHECK(estrategia IN ('CSS','JSON_LD','VTEX','DEMO','AUTO'));
ALTER TABLE producto_fuente ADD COLUMN IF NOT EXISTS busqueda_automatica BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE resultado DROP CONSTRAINT IF EXISTS resultado_ejecucion_id_key;
CREATE UNIQUE INDEX IF NOT EXISTS resultado_ejecucion_url_idx ON resultado(ejecucion_id,url);
ALTER TABLE ejecucion DROP CONSTRAINT IF EXISTS ejecucion_estado_check;
ALTER TABLE ejecucion ADD CONSTRAINT ejecucion_estado_check CHECK(estado IN ('EN_CURSO','OK','ERROR','SIN_RESULTADOS'));
ALTER TABLE ejecucion ADD COLUMN IF NOT EXISTS resumen_busqueda JSONB;
