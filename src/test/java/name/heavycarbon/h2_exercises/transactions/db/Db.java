package name.heavycarbon.h2_exercises.transactions.db;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.commons.DbHelpers;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// ---
// Access to the DB: It knows how to run queries.
// An instance of this class is autowired with a JdbcTemplate.
// None of the methods of this class are marked "transactional", we will mark methods
// calling Db methods thusly.
// ---

@Slf4j
@Component
public class Db {

    private final @NotNull JdbcTemplate jdbcTemplate;

    @Autowired
    public Db(@NotNull JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---
    // Table and field naming
    // Convention:
    // - The table name is plural, lowercase, unquoted.
    // - The field names are lowercase, unquoted.
    // - The schema name is used and unquoted.
    // - Table names are qualified with the schema name in all queries.
    //   If table names are unqualified, they are in schema PUBLIC, i.e. at the "toplevel" of H2.
    // Note that in H2, unquoted names are by default converted to uppercase, so
    // the actual scheman, table and field names in the database will all appear in uppercase.
    // https://h2database.com/html/grammar.html#name

    public final static String schemaName = "testing_transactions";

    public final static String tableName_stuff = "stuff";

    public final static String fqTableName_stuff = String.format("%s.%s", schemaName, tableName_stuff);

    public final static String field_id = "id";
    public final static String field_ensemble = "ensemble"; // used in selection predicates
    public final static String field_payload = "payload"; // arbitrary text payload

    // ---

    private void createTableWithAutoincrementId() {
        final String sqlRaw = "CREATE TABLE IF NOT EXISTS "
                + fqTableName_stuff
                + " ("
                + field_id + " INTEGER AUTO_INCREMENT PRIMARY KEY, "
                + field_ensemble + " INTEGER NOT NULL, "
                + field_payload + " VARCHAR(50) NOT NULL "
                + " )";
        jdbcTemplate.execute(sqlRaw);
    }

    // ---

    private void createTable() {
        final String sqlRaw = "CREATE TABLE IF NOT EXISTS "
                + fqTableName_stuff
                + " ("
                + field_id + " INTEGER PRIMARY KEY, "
                + field_ensemble + " INTEGER NOT NULL, "
                + field_payload + " VARCHAR(50) NOT NULL "
                + " )";
        jdbcTemplate.execute(sqlRaw);
    }

    // ---
    // "cleanup" indicates whether the tables and the schema should be dropped
    // before recreating them.
    // ---

    public enum AutoIncrementing {Yes, No}

    public enum CleanupFirst {Yes, No}

    public void setupDatabase(@NotNull AutoIncrementing autoIncrementing, @NotNull CleanupFirst cleanupFirst) {
        if (cleanupFirst == CleanupFirst.Yes) {
            DbHelpers.dropSchema(schemaName, true, jdbcTemplate);
        }
        DbHelpers.createSchema(schemaName, jdbcTemplate);
        if (autoIncrementing == AutoIncrementing.Yes) {
            createTableWithAutoincrementId();
        } else {
            createTable();
        }
    }

    // ---
    // Retrieving
    // ---

    private static Stuff rowMapper_stuff(@NotNull ResultSet row, int rowNum) throws SQLException {
        final StuffId id = new StuffId(row.getInt(field_id));
        final EnsembleId ensembleId = new EnsembleId(row.getInt(field_ensemble));
        final String payload = row.getString(field_payload);
        return new Stuff(id, ensembleId, payload);
    }

    public @NotNull Optional<Stuff> readById(@NotNull StuffId stuffId) {
        final String sql = "SELECT "
                + field_id + ","
                + field_ensemble + ","
                + field_payload
                + " FROM "
                + fqTableName_stuff
                + " WHERE "
                + field_id + " = ?";
        List<Stuff> list = jdbcTemplate.query(sql, Db::rowMapper_stuff, stuffId.getRaw());
        if (list.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(list.get(0));
        }
    }

    public @NotNull List<Stuff> readByEnsemble(@NotNull EnsembleId ensembleId) {
        final String sql = "SELECT "
                + field_id + ","
                + field_ensemble + ","
                + field_payload
                + " FROM "
                + fqTableName_stuff
                + " WHERE "
                + field_ensemble + " = ? "
                + " ORDER BY "
                + field_id;
        return jdbcTemplate.query(sql, Db::rowMapper_stuff, ensembleId.getRaw());
    }

    public @NotNull List<Stuff> readAll() {
        final String sql = "SELECT "
                + field_id + ","
                + field_ensemble + ","
                + field_payload
                + " FROM "
                + fqTableName_stuff
                + " ORDER BY "
                + field_id;
        return jdbcTemplate.query(sql, Db::rowMapper_stuff);
    }

    // See https://stackoverflow.com/questions/8247970/using-like-wildcard-in-prepared-statement
    // http://h2database.com/html/grammar.html?highlight=escape&search=ESCAPE#like_predicate_right_hand_side

    private static String escapeSearchString(String searchString) {
        // The escape character is chosen to be "!"
        return searchString.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    public @NotNull List<Stuff> readByPayloadSuffix(@NotNull String suffix) {
        final String sql = "SELECT "
                + field_id + ","
                + field_ensemble + ","
                + field_payload
                + " FROM "
                + fqTableName_stuff
                + " WHERE "
                + field_payload + " LIKE CONCAT('%',?) ESCAPE '!' " // Does selecting the escape character like this work?
                + " ORDER BY "
                + field_id;
        return jdbcTemplate.query(sql, Db::rowMapper_stuff, escapeSearchString(suffix));
    }

    public @NotNull List<Stuff> readByEnsembleAndPayloadSuffix(@NotNull EnsembleId ensembleId, @NotNull String suffix) {
        final String sql = "SELECT "
                + field_id + ","
                + field_ensemble + ","
                + field_payload
                + " FROM "
                + fqTableName_stuff
                + " WHERE "
                + field_payload + " LIKE CONCAT('%',?) ESCAPE '!' "
                + " AND "
                + field_ensemble + " = ? "
                + " ORDER BY "
                + field_id;
        return jdbcTemplate.query(sql, Db::rowMapper_stuff, escapeSearchString(suffix), ensembleId.getRaw());
    }

    // Make a map for inserting a row into the table with the auto incrementing id

    static Map<String, Object> makeMapForRow(@NotNull EnsembleId ensembleId, @NotNull String payload) {
        final Map<String, Object> res = new HashMap<>();
        res.put(field_ensemble, ensembleId.getRaw());
        res.put(field_payload, payload);
        return res;
    }

    // Insert a row into the table with the auto incrementing id, return the new id.

    public StuffId insert(@NotNull EnsembleId ensembleId, @NotNull String payload) {
        // Not sure whether I need to additionally specify usingColumns() ???
        final Number num = new SimpleJdbcInsert(jdbcTemplate)
                .withSchemaName(schemaName)
                .withTableName(tableName_stuff)
                .usingGeneratedKeyColumns(field_id)
                .executeAndReturnKey(makeMapForRow(ensembleId, payload));
        return new StuffId(num.intValue());
    }

    // Make a map for inserting a row into the table without the auto incrementing id

    static Map<String, Object> makeMapForRow(@NotNull Stuff stuff) {
        final Map<String, Object> res = new HashMap<>();
        res.put(field_id, stuff.getId().getRaw());
        res.put(field_ensemble, stuff.getEnsembleId().getRaw());
        res.put(field_payload, stuff.getPayload());
        return res;
    }

    // Insert a row into the table without the auto incrementing id, return nothing

    public void insert(@NotNull Stuff stuff) {
        // Not sure whether I need to additionally specify usingColumns() ???
        final int count = new SimpleJdbcInsert(jdbcTemplate)
                .withSchemaName(schemaName)
                .withTableName(tableName_stuff)
                .execute(makeMapForRow(stuff));
        assert count == 1;
    }

    // Update the ensembleId a row given an id (row may not exist).
    // Return true if found.

    public boolean updatePayloadById(@NotNull StuffId stuffId, @NotNull String payload) {
        final int count = jdbcTemplate.update("UPDATE "
                + fqTableName_stuff
                + " SET "
                + field_payload
                + " = ? "
                + " WHERE "
                + field_id
                + " = ?", payload, stuffId.getRaw());
        assert count >= 0 && count <= 1;
        return count == 1;
    }

    // Update the ensembleId a row given an id (row may not exist).
    // Return true if found.

    public boolean updateEnsembleById(@NotNull StuffId stuffId, @NotNull EnsembleId ensembleId) {
        final int count = jdbcTemplate.update("UPDATE "
                + fqTableName_stuff
                + " SET "
                + field_ensemble
                + " = ? "
                + " WHERE "
                + field_id
                + " = ?", ensembleId.getRaw(), stuffId.getRaw());
        assert count >= 0 && count <= 1;
        return count == 1;
    }

    // Delete a row given an id (row may not exist).
    // Return true if found.

    public boolean deleteById(@NotNull StuffId stuffId) {
        final int count = jdbcTemplate.update("DELETE FROM "
                + fqTableName_stuff
                + " WHERE "
                + field_id
                + " = ?", stuffId.getRaw());
        assert count >= 0 && count <= 1;
        return count == 1;
    }
}
