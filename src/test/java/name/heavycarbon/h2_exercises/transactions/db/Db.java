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
// Access to the DB: This class knows how to run queries (it is a "Data Access Object").
// A single instance of this class is autowired with a JdbcTemplate instance by Spring.
// None of the methods of this class are marked "transactional".
// However, methods calling methods of this class will be marked "transactional".
//
// This class could create a good part of the query texts at startup instead of
// again and again at method call, but we will leave that for later.
//
// Table and field naming
// - - - - - - - - - - - -
//
// Convention:
// - The table name is plural, lowercase, unquoted (that doesn't work with the name "stuff" though)
// - The field names are lowercase, unquoted.
// - A schema name is used and unquoted.
// - Table names are qualified with the schema name in all queries.
//
// Note that if table names are unqualified, they are added by H2 to schema "PUBLIC", and it looks
// as if they were in no schema at all in the H2 web console.
//
// Note that in H2, unquoted names are by default **converted to uppercase**, so
// the actual schema name, table name and field names will all actually be expressed in uppercase.
// https://h2database.com/html/grammar.html#name
// ---

@Slf4j
@Component
public class Db {

    @Autowired
    private @NotNull JdbcTemplate jdbcTemplate;

    public final static String schemaName = "testing_transactions";

    // The table used in most tests: just "stuff"

    public final static String tableName_stuff = "stuff";
    public final static String fqTableName_stuff = String.format("%s.%s", schemaName, tableName_stuff);

    // Some tests use an "alternate stuff" table

    public final static String tableName_alternateStuff = "alternate_stuff";
    public final static String fqTableName_alternateStuff = String.format("%s.%s", schemaName, tableName_alternateStuff);

    // Fields (columns) encountered in tables "stuff" and "alternate stuff"

    public final static String field_id = "id";
    public final static String field_ensemble = "ensemble"; // used in selection predicates
    public final static String field_payload = "payload"; // arbitrary text payload

    // ---

    private void createStuffTableWithAutoincrementId() {
        final String sql = "CREATE TABLE IF NOT EXISTS "
                + fqTableName_stuff
                + " ("
                + field_id + " INTEGER AUTO_INCREMENT PRIMARY KEY, "
                + field_ensemble + " INTEGER NOT NULL, "
                + field_payload + " VARCHAR(50) NOT NULL "
                + ")";
        jdbcTemplate.execute(sql);
    }

    // ---

    public void createStuffTable() {
        createSomeStuffTable(false);
    }

    public void createAlternateStuffTable() {
        createSomeStuffTable(true);
    }

    // ---

    private void createSomeStuffTable(boolean alternate) {
        final String table = (alternate ? fqTableName_alternateStuff : fqTableName_stuff);
        final String sql = "CREATE TABLE IF NOT EXISTS "
                + table
                + " ("
                + field_id + " INTEGER PRIMARY KEY, "
                + field_ensemble + " INTEGER NOT NULL, "
                + field_payload + " VARCHAR(50) NOT NULL "
                + ")";
        jdbcTemplate.execute(sql);
    }

    // ---
    // "Rejuvenation" means dropping the schema (if it exists) with all in it,
    // then re-creating it.
    // ---

    public void rejuvenateSchema() {
        DbHelpers.dropSchemaIfExists(schemaName, DbHelpers.Cascade.Yes, jdbcTemplate);
        DbHelpers.createSchema(schemaName, jdbcTemplate);
    }

    // ---
    // Retrieve 0 or 1 record of type "Stuff" by primary key "id"
    // ---

    private static Stuff rowMapper_stuff(@NotNull ResultSet row, int rowNum) throws SQLException {
        final StuffId id = new StuffId(row.getInt(field_id));
        final EnsembleId ensembleId = new EnsembleId(row.getInt(field_ensemble));
        final String payload = row.getString(field_payload);
        return new Stuff(id, ensembleId, payload);
    }

    public @NotNull Optional<Stuff> readById(@NotNull StuffId stuffId) {
        return readById(stuffId, false);
    }

    public @NotNull Optional<Stuff> readAlternateById(@NotNull StuffId stuffId) {
        return readById(stuffId, true);
    }

    private @NotNull Optional<Stuff> readById(@NotNull StuffId stuffId, boolean alternate) {
        final String localTableName = (alternate ? fqTableName_alternateStuff : fqTableName_stuff);
        final String sql = "SELECT "
                + field_id + ","
                + field_ensemble + ","
                + field_payload
                + " FROM "
                + localTableName
                + " WHERE "
                + field_id + " = ?";
        List<Stuff> list = jdbcTemplate.query(sql, Db::rowMapper_stuff, stuffId.getRaw());
        if (list.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(list.get(0));
        }
    }

    // ---
    // Retrieve 0...n records of type "Stuff" returned as list ordered by primary key,
    // by matching on "ensemble id".
    // ---

    public @NotNull List<Stuff> readByEnsemble(@NotNull EnsembleId ensembleId) {
        return readByEnsemble(ensembleId, false);
    }

    public @NotNull List<Stuff> readAlternateByEnsemble(@NotNull EnsembleId ensembleId) {
        return readByEnsemble(ensembleId, true);
    }

    private @NotNull List<Stuff> readByEnsemble(@NotNull EnsembleId ensembleId, boolean alternate) {
        final String localTableName = (alternate ? fqTableName_alternateStuff : fqTableName_stuff);
        final String sql = "SELECT "
                + field_id + ","
                + field_ensemble + ","
                + field_payload
                + " FROM "
                + localTableName
                + " WHERE "
                + field_ensemble + " = ? "
                + " ORDER BY "
                + field_id;
        return jdbcTemplate.query(sql, Db::rowMapper_stuff, ensembleId.getRaw());
    }

    // ---
    // Retrieve everything, yielding 0...n records of type "Stuff", returned as list ordered by primary key
    // by matching on "ensemble id".
    // ---

    public @NotNull List<Stuff> readAll() {
        return readAll(false);
    }

    public @NotNull List<Stuff> readAlternateAll() {
        return readAll(true);
    }

    private @NotNull List<Stuff> readAll(boolean alternate) {
        final String localTableName = (alternate ? fqTableName_alternateStuff : fqTableName_stuff);
        final String sql = "SELECT "
                + field_id + ","
                + field_ensemble + ","
                + field_payload
                + " FROM "
                + localTableName
                + " ORDER BY "
                + field_id;
        return jdbcTemplate.query(sql, Db::rowMapper_stuff);
    }

    // See https://stackoverflow.com/questions/8247970/using-like-wildcard-in-prepared-statement
    // and http://h2database.com/html/grammar.html?highlight=escape&search=ESCAPE#like_predicate_right_hand_side

    private static String escapeSearchString(String searchString) {
        // The escape character is chosen to be "!"
        return searchString.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    // ---
    // Retrieve 0...n records of type "Stuff" where the payload ends in "suffix", returned as
    // list ordered by primary key. This is used when trying to elicit "phantom reads".
    // ---

    public @NotNull List<Stuff> readByPayloadSuffix(@NotNull String suffix) {
        final String sql = "SELECT "
                + field_id + ","
                + field_ensemble + ","
                + field_payload
                + " FROM "
                + fqTableName_stuff
                + " WHERE "
                + field_payload + " LIKE CONCAT('%',?) ESCAPE '!' "
                + " ORDER BY "
                + field_id;
        return jdbcTemplate.query(sql, Db::rowMapper_stuff, escapeSearchString(suffix));
    }

    // ---
    // Retrieve 0...n records of type "Stuff" where the payload ends in "suffix" and the
    // "ensemble" field matches "ensembleId", returned as list ordered by primary key.
    // This is used when trying to elicit "phantom reads".
    // ---

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

    // ---
    // Make a map for inserting a row into the table with the auto incrementing id
    // ---

    private static Map<String, Object> makeMapForRow(@NotNull EnsembleId ensembleId, @NotNull String payload) {
        final Map<String, Object> res = new HashMap<>();
        res.put(field_ensemble, ensembleId.getRaw());
        res.put(field_payload, payload);
        return res;
    }

    // ---
    // Insert a row into the table with the auto incrementing id, return the new id.
    // ---

    public StuffId insert(@NotNull EnsembleId ensembleId, @NotNull String payload) {
        // Not sure whether I need to additionally specify usingColumns() ???
        final Number num = new SimpleJdbcInsert(jdbcTemplate)
                .withSchemaName(schemaName)
                .withTableName(tableName_stuff)
                .usingGeneratedKeyColumns(field_id)
                .executeAndReturnKey(makeMapForRow(ensembleId, payload));
        return new StuffId(num.intValue());
    }

    // ---
    // Make a map for inserting a row into the table that has no auto incrementing id
    // ---

    private static Map<String, Object> makeMapForRow(@NotNull Stuff stuff) {
        final Map<String, Object> res = new HashMap<>();
        res.put(field_id, stuff.getId().getRaw());
        res.put(field_ensemble, stuff.getEnsembleId().getRaw());
        res.put(field_payload, stuff.getPayload());
        return res;
    }

    // ---
    // Insert a row into the table without the auto incrementing id, return nothing
    // ---

    public void insert(@NotNull Stuff stuff) {
        insert(stuff, false);
    }

    // ---
    // Insert a row into the table without the auto incrementing id, return nothing
    // ---

    public void insertAlternate(@NotNull Stuff stuff) {
        insert(stuff, true);
    }

    // ---
    // Insert a row into the table without the auto incrementing id, return nothing
    // ---

    private void insert(@NotNull Stuff stuff, boolean alternate) {
        final String localTableName = (alternate ? tableName_alternateStuff : tableName_stuff);
        // Not sure whether I need to additionally specify usingColumns() ???
        final int count = new SimpleJdbcInsert(jdbcTemplate)
                .withSchemaName(schemaName)
                .withTableName(localTableName)
                .execute(makeMapForRow(stuff));
        assert count == 1;
    }

    // ---
    // Update the "payload" string of the row given by the primary key "id" (row may not exist).
    // Return true if found.
    // ---

    public boolean updatePayloadById(@NotNull StuffId stuffId, @NotNull String payload) {
        return updatePayloadById(stuffId, payload, false);
    }

    // ---
    // Update the "payload" string of the row given by the primary key "id" (row may not exist).
    // Return true if found.
    // ---

    public boolean updatePayloadByIdAlternate(@NotNull StuffId stuffId, @NotNull String payload) {
        return updatePayloadById(stuffId, payload, true);
    }

    // ---
    // Update the "payload" string of the row given by the primary key "id" (row may not exist).
    // Return true if found.
    // ---

    private boolean updatePayloadById(@NotNull StuffId stuffId, @NotNull String payload, boolean alternate) {
        final String localTableName = (alternate ? fqTableName_alternateStuff : fqTableName_stuff);
        final int count = jdbcTemplate.update("UPDATE "
                + localTableName
                + " SET "
                + field_payload
                + " = ? "
                + " WHERE "
                + field_id
                + " = ?", payload, stuffId.getRaw());
        assert count >= 0 && count <= 1;
        return count == 1;
    }

    // ---
    // Update the "ensemble id" integer of the row given by the primary key "id" (row may not exist).
    // Return true if found.
    // ---

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

    // ---
    // Delete the row given by the primary key "id" (row may not exist). Return true if found.
    // ---

    public boolean deleteById(@NotNull StuffId stuffId) {
        return deleteById(stuffId, false);
    }

    // ---
    // Delete the row given by the primary key "id" (row may not exist). Return true if found.
    // ---

    public boolean deleteByIdAlternate(@NotNull StuffId stuffId) {
        return deleteById(stuffId, true);
    }

    // ---
    // Delete the row given by the primary key "id" (row may not exist). Return true if found.
    // ---

    public boolean deleteById(@NotNull StuffId stuffId, boolean alternate) {
        final String localTableName = (alternate ? fqTableName_alternateStuff : fqTableName_stuff);
        final int count = jdbcTemplate.update("DELETE FROM "
                + localTableName
                + " WHERE "
                + field_id
                + " = ?", stuffId.getRaw());
        assert count >= 0 && count <= 1;
        return count == 1;
    }
}
