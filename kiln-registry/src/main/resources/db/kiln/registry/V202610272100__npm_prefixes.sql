-- Unscoped npm names a namespace claims by prefix, as cargo_prefixes does for crates (REG-1).
ALTER TABLE namespaces ADD COLUMN npm_prefixes text[] NOT NULL DEFAULT '{}';
