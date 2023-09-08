package name.heavycarbon.h2_exercises.commons;

import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class DbHelpers {

    public enum Cascade { Yes, No }

    // ----
    // https://h2database.com/html/commands.html#create_schema
    // ----

    public static void createSchema(@NotNull String schemaName, @NotNull JdbcTemplate jdbcTemplate) {
        final String sql = "CREATE SCHEMA IF NOT EXISTS " + schemaName;
        jdbcTemplate.execute(sql);
    }

    // ---
    // https://h2database.com/html/commands.html#drop_schema
    // ---

    public static void dropSchemaIfExists(@NotNull String schemaName, @NotNull Cascade cascade, @NotNull JdbcTemplate jdbcTemplate) {
        final String sql = "DROP SCHEMA IF EXISTS " + schemaName + (cascade == Cascade.Yes ? " CASCADE" : "");
        jdbcTemplate.execute(sql);
    }

}
