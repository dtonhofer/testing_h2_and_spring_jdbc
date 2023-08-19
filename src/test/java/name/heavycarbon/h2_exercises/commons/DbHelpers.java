package name.heavycarbon.h2_exercises.commons;

import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;

import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class DbHelpers {

    // ----
    // https://h2database.com/html/commands.html#create_schema
    // ----

    public static void createSchema(@NotNull String schemaName, @NotNull JdbcTemplate jdbcTemplate) {
        final String sql = String.format("CREATE SCHEMA IF NOT EXISTS %s", schemaName);
        jdbcTemplate.execute(sql);
    }

    // ---
    // https://h2database.com/html/commands.html#drop_schema
    // ---

    public static void dropSchema(@NotNull String schemaName, boolean cascade, @NotNull JdbcTemplate jdbcTemplate) {
        final String sqlRaw = "DROP SCHEMA IF EXISTS %s " + (cascade ? "CASCADE" : "");
        final String sql = String.format(sqlRaw, schemaName);
        jdbcTemplate.execute(sql);
    }

}
