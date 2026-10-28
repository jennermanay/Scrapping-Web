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
