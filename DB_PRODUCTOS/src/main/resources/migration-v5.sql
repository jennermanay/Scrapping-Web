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
