/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.Record;

/**
 * Checks from the catalog that the schema keeps organizations apart (DB-6). Row-level security
 * fails silently when it's missing, not forced or too permissive, so every organization table must:
 *
 * <ul>
 *   <li>have a non-null {@code uuid} {@code organization_id} column
 *   <li>have row-level security enabled and forced
 *   <li>have only the organization policy as a permissive policy, for every command
 *   <li>lead every unique index, the primary key included, with {@code organization_id}
 *   <li>include {@code organization_id} in every foreign key, on both sides when it references
 *       another organization table
 * </ul>
 *
 * <p>Across the schema: no {@code SECURITY DEFINER} function, and every view created with {@code
 * security_invoker}. The role it runs as, the runtime role, must not be a superuser, bypass
 * row-level security, or own anything.
 */
public final class SchemaLinter {

    /**
     * The tables with no organization column, listed explicitly so a new one is a deliberate
     * change here. They hold no organization's content.
     */
    public static final Set<String> INSTANCE_TABLES = Set.of(
            "flyway_schema_history",
            "organizations",
            "instance_connectors",
            "web_sessions",
            "instance_blocks",
            "leases",
            "used_jtis");

    /** The only permissive policy an organization table may have, as Postgres prints it. */
    static final String ORGANIZATION_POLICY =
            "(organization_id = (current_setting('app.organization_id'::text))::uuid)";

    private SchemaLinter() {}

    /** Returns every problem found in the current schema; empty when it's organization-safe. */
    public static List<String> problems(DSLContext db) {
        List<String> problems = new ArrayList<>();
        checkTables(db, problems);
        checkPolicies(db, problems);
        checkUniqueIndexes(db, problems);
        checkForeignKeys(db, problems);
        checkFunctionsAndViews(db, problems);
        checkRuntimeRole(db, problems);
        return problems;
    }

    private static void checkTables(DSLContext db, List<String> problems) {
        for (Record table : db.fetch("""
                select c.relname as name, c.relrowsecurity as enabled, c.relforcerowsecurity as forced,
                       exists (select 1 from pg_attribute a
                               where a.attrelid = c.oid and a.attname = 'organization_id' and not a.attisdropped
                                 and a.attnotnull and a.atttypid = 'uuid'::regtype) as has_column,
                       exists (select 1 from pg_policy p where p.polrelid = c.oid) as has_policy
                from pg_class c join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = current_schema() and c.relkind in ('r', 'p')
                order by c.relname""")) {
            String name = table.get("name", String.class);
            if (INSTANCE_TABLES.contains(name)) {
                continue;
            }
            if (!table.get("has_column", Boolean.class)) {
                problems.add(name + " has no non-null uuid organization_id column");
            }
            if (!table.get("enabled", Boolean.class) || !table.get("forced", Boolean.class)) {
                problems.add(name + " doesn't have row-level security enabled and forced");
            }
            if (!table.get("has_policy", Boolean.class)) {
                problems.add(name + " has no row-level security policy");
            }
        }
    }

    private static void checkPolicies(DSLContext db, List<String> problems) {
        for (Record policy : db.fetch("""
                select c.relname as table_name, p.polname as name, p.polcmd = '*' as all_commands,
                       pg_get_expr(p.polqual, p.polrelid) as qual,
                       pg_get_expr(p.polwithcheck, p.polrelid) as with_check
                from pg_policy p join pg_class c on c.oid = p.polrelid
                join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = current_schema() and p.polpermissive
                order by c.relname, p.polname""")) {
            String table = policy.get("table_name", String.class);
            String withCheck = policy.get("with_check", String.class);
            boolean exact = policy.get("all_commands", Boolean.class)
                    && ORGANIZATION_POLICY.equals(policy.get("qual", String.class))
                    && (withCheck == null || ORGANIZATION_POLICY.equals(withCheck));
            if (!INSTANCE_TABLES.contains(table) && !exact) {
                problems.add(table + " has permissive policy " + policy.get("name", String.class)
                        + " that isn't exactly the organization policy for every command");
            }
        }
    }

    private static void checkUniqueIndexes(DSLContext db, List<String> problems) {
        for (Record index : db.fetch("""
                select c.relname as table_name, i.relname as name
                from pg_index x join pg_class c on c.oid = x.indrelid
                join pg_class i on i.oid = x.indexrelid
                join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = current_schema() and x.indisunique
                  and coalesce((select a.attname from pg_attribute a
                                where a.attrelid = c.oid and a.attnum = x.indkey[0]), '') <> 'organization_id'
                order by c.relname, i.relname""")) {
            String table = index.get("table_name", String.class);
            if (!INSTANCE_TABLES.contains(table)) {
                problems.add(table + " has unique index " + index.get("name", String.class)
                        + " that doesn't start with organization_id");
            }
        }
    }

    private static void checkForeignKeys(DSLContext db, List<String> problems) {
        for (Record key : db.fetch("""
                select c.relname as table_name, k.conname as name, t.relname as target,
                       exists (select 1 from pg_attribute a where a.attrelid = k.conrelid
                               and a.attnum = any (k.conkey) and a.attname = 'organization_id') as from_has,
                       exists (select 1 from pg_attribute a where a.attrelid = k.confrelid
                               and a.attnum = any (k.confkey) and a.attname = 'organization_id') as to_has
                from pg_constraint k join pg_class c on c.oid = k.conrelid
                join pg_class t on t.oid = k.confrelid
                join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = current_schema() and k.contype = 'f'
                order by c.relname, k.conname""")) {
            String table = key.get("table_name", String.class);
            if (INSTANCE_TABLES.contains(table)) {
                continue;
            }
            boolean toOrganizationTable = !INSTANCE_TABLES.contains(key.get("target", String.class));
            if (!key.get("from_has", Boolean.class) || (toOrganizationTable && !key.get("to_has", Boolean.class))) {
                problems.add(table + " has foreign key " + key.get("name", String.class)
                        + " that doesn't include organization_id");
            }
        }
    }

    private static void checkFunctionsAndViews(DSLContext db, List<String> problems) {
        for (Record function : db.fetch("""
                select p.proname as name from pg_proc p join pg_namespace n on n.oid = p.pronamespace
                where n.nspname = current_schema() and p.prosecdef order by p.proname""")) {
            problems.add("function " + function.get("name", String.class) + " is SECURITY DEFINER");
        }
        for (Record view : db.fetch("""
                select c.relname as name from pg_class c join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = current_schema() and c.relkind in ('v', 'm')
                  and not coalesce('security_invoker=true' = any (c.reloptions), false)
                order by c.relname""")) {
            problems.add("view " + view.get("name", String.class) + " isn't created with security_invoker");
        }
    }

    private static void checkRuntimeRole(DSLContext db, List<String> problems) {
        Record role = db.fetchSingle("""
                select r.rolname as name, r.rolsuper as superuser, r.rolbypassrls as bypass,
                       exists (select 1 from pg_class c where c.relowner = r.oid)
                       or exists (select 1 from pg_proc p where p.proowner = r.oid)
                       or exists (select 1 from pg_namespace s where s.nspowner = r.oid) as owns
                from pg_roles r where r.rolname = current_user""");
        String name = role.get("name", String.class);
        if (role.get("superuser", Boolean.class) || role.get("bypass", Boolean.class)) {
            problems.add("runtime role " + name + " is a superuser or bypasses row-level security");
        }
        if (role.get("owns", Boolean.class)) {
            problems.add("runtime role " + name + " owns tables, functions or schemas");
        }
    }
}
