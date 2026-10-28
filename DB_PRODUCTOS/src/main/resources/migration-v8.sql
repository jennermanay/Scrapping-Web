CREATE TABLE IF NOT EXISTS alerta_decision_precio (
 id BIGSERIAL PRIMARY KEY,alerta_id BIGINT NOT NULL UNIQUE REFERENCES alerta(id),producto_id BIGINT NOT NULL REFERENCES producto(id),usuario_id BIGINT NOT NULL REFERENCES usuario(id),
 accion TEXT NOT NULL CHECK(accion IN ('KEEP','MATCH','LOWER')),precio_anterior NUMERIC(12,2) NOT NULL CHECK(precio_anterior>0),precio_nuevo NUMERIC(12,2) NOT NULL CHECK(precio_nuevo>0),
 costo NUMERIC(12,2) NOT NULL CHECK(costo>0),utilidad NUMERIC(12,2) NOT NULL,margen NUMERIC(18,2) NOT NULL,observacion TEXT NOT NULL DEFAULT '',fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
