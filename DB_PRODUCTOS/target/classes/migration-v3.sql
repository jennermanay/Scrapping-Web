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
