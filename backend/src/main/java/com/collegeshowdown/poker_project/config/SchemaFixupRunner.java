package com.collegeshowdown.poker_project.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-off, idempotent fixes for things {@code ddl-auto=update} can't do
 * itself (it only ever adds columns, never drops constraints). Safe to
 * run on every startup — each fix no-ops once applied.
 */
@Component
public class SchemaFixupRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaFixupRunner.class);
    private final JdbcTemplate jdbcTemplate;

    public SchemaFixupRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        dropUniqueConstraint("players", "username");
    }

    /**
     * The "username" column now stores a player's real display name
     * (not a chosen unique handle), so any leftover UNIQUE constraint
     * on it must go — duplicate real names are expected.
     */
    private void dropUniqueConstraint(String table, String column) {
        List<String> constraints = jdbcTemplate.queryForList(
                """
                SELECT tc.constraint_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                WHERE tc.constraint_type = 'UNIQUE'
                  AND tc.table_name = ?
                  AND kcu.column_name = ?
                """,
                String.class, table, column);

        for (String constraintName : constraints) {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT \"" + constraintName + "\"");
            log.info("Dropped stale unique constraint {} on {}.{}", constraintName, table, column);
        }
    }
}
