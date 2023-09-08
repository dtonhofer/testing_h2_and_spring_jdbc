package name.heavycarbon.h2_exercises.storing_instants;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.commons.DbHelpers;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureJdbc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

// ---
// A JUnit5 test that consist in writing a "java.time.Instant" instance to an H2 database table.
// The table consists of several columns of different database types. For each type, we check whether
// writing the "java.time.Instant" and then retrieving it indeed give us the same "java.time.Instant"
// as the one that was written.
//
// More on "java.time" package:
// We do not use any of the classes of the old-as-dirt "java.util" time-related classes,
// including "java.util.Date" and "java.util.Calendar".
//
// https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/package-summary.html
// https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Instant.html
// https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/ZoneId.html
// https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/ZonedDateTime.html
// https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/format/DateTimeFormatter.html
// https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/temporal/TemporalAccessor.html
// https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/temporal/TemporalField.html
// https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/temporal/ChronoField.html
//
// More on H2 data types:
//
// http://h2database.com/html/datatypes.html
//
// ---

@Slf4j
@AutoConfigureJdbc
@SpringBootTest(classes = {TestStoringInstants.class})
public class TestStoringInstants {

    // This class cannot be autowired by constructor (it would need some additional JUnit glue
    // code for this). We just have a JdbcTemplate field that is marked as "autowired".

    @Autowired
    private @NotNull JdbcTemplate jdbcTemplate;

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
    // ---

    public final static String schemaName = "testing_datetime";

    public final static String tableName_instants = "instants";

    public final static String fqTableName_instants = String.format("%s.%s", schemaName, tableName_instants);

    private final static String field_id = "id";
    private final static String field_instant_as_timestamp_with_tz = "instant_as_timestamp_with_tz";
    private final static String field_instant_as_timestamp_with_tz_no_fractions = "instant_as_timestamp_with_tz_no_fractions";
    private final static String field_instant_as_java_object = "instant_as_java_object";
    private final static String field_instant_as_seconds_since_epoch_explicit = "instant_as_seconds_since_epoch_explicit";
    private final static String field_instant_as_explicit_string = "instant_as_explicit_string";
    private final static String field_instant_as_implicit_string = "instant_as_implicit_string";
    private final static String field_instant_as_explicit_date_utc = "instant_as_explicit_date_utc";
    private final static String field_instant_as_explicit_time_utc = "instant_as_explicit_time_utc";

    // ----
    // https://h2database.com/html/commands.html#create_table
    // http://h2database.com/html/datatypes.html
    // ----

    private void createTable() {
        // A "TIMESTAMP" type without timezone makes NO SENSE for "Instant". Thus use "TIMESTAMP WITH TIME ZONE".
        // A "DATETIME" type is allowed (but undocumented) it seems to be the same as "TIMESTAMP" without timezone.
        // If you use "TIMEZONE"/"DATETIME" the read-retrieve back and forth will wrongly use the configured
        // server time zone (in INFORMATION_SCHEMA.SETTINGS, "TIME ZONE") but only on one part of the
        // back-and-forth trip. You will thus end up with garbage. Don't use it.
        final String sql = "CREATE TABLE IF NOT EXISTS "
                + fqTableName_instants
                + " ("
                + field_id + " INTEGER AUTO_INCREMENT PRIMARY KEY, "
                + field_instant_as_timestamp_with_tz + " TIMESTAMP WITH TIME ZONE DEFAULT NULL, "
                + field_instant_as_timestamp_with_tz_no_fractions + " TIMESTAMP(0) WITH TIME ZONE DEFAULT NULL, "
                + field_instant_as_java_object + " JAVA_OBJECT DEFAULT NULL, "
                + field_instant_as_seconds_since_epoch_explicit + " BIGINT DEFAULT NULL, "
                + field_instant_as_explicit_string + " VARCHAR(100) DEFAULT NULL, "
                + field_instant_as_implicit_string + " VARCHAR(100) DEFAULT NULL, "
                + field_instant_as_explicit_date_utc + " DATE DEFAULT NULL, "
                + field_instant_as_explicit_time_utc + " TIME DEFAULT NULL"
                + ")";
        jdbcTemplate.execute(sql);
    }

    // ---
    // "cleanup" indicates whether the tables and the schema should bd dropped when the
    // test finishes. If you run a volatile database (in-memory), there is no need to clean
    // up. If you want to inspect results later, you may not want to clean up.
    // ---

    private void setupDatabase(boolean cleanupFirst) {
        if (cleanupFirst) {
            DbHelpers.dropSchemaIfExists(schemaName, DbHelpers.Cascade.Yes, jdbcTemplate);
        }
        DbHelpers.createSchema(schemaName, jdbcTemplate);
        createTable();
    }

    // ---

    private static @NotNull Map<String, Object> makeMapForRow(@NotNull Instant instant) {
        final Map<String, Object> res = new HashMap<>();
        {
            // http://h2database.com/html/datatypes.html#timestamp_with_time_zone_type
            // By default, fractional seconds to precision 6 digits (microseconds)
            // In the console, this appears as "2020-12-04 10:00:00+00"
            res.put(field_instant_as_timestamp_with_tz, instant);
            res.put(field_instant_as_timestamp_with_tz_no_fractions, instant);
        }
        {
            // Serializes the Java object into the field, appears as hexdump in the console.
            // http://h2database.com/html/datatypes.html#java_object_type
            res.put(field_instant_as_java_object, instant);
        }
        {
            // Writes a "long" into a BIGINT field, which corresponds to "long".
            // http://h2database.com/html/datatypes.html#bigint_type
            // Same as instant.getLong(ChronoField.INSTANT_SECONDS).
            // This does NOT work "implicitly", where you would pass the instant directly.
            res.put(field_instant_as_seconds_since_epoch_explicit, instant.getEpochSecond());
        }
        {
            // Write explicitly to VARCHAR, you get "2023-08-08T10:00:00Z" (ISO_INSTANT format)
            // https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/format/DateTimeFormatter.html#ISO_INSTANT
            res.put(field_instant_as_explicit_string, instant.toString());
        }
        {
            // Write implicitly to VARCHAR, same as using instant.toString() first.
            res.put(field_instant_as_implicit_string, instant);
        }
        {
            // In this case, the date and time part are written separately and there is no
            // longer any timezone information, although both values are apparently as in the UTC timezone.
            res.put(field_instant_as_explicit_date_utc, LocalDate.ofInstant(instant, ZoneId.of("UTC")));
            res.put(field_instant_as_explicit_time_utc, LocalTime.ofInstant(instant, ZoneId.of("UTC")));
        }
        return res;
    }

    // Note: The "insert" works perfectly well if the columns don't exist!

    private @NotNull InstantId insert(@NotNull Instant instant) {
        final Number num = new SimpleJdbcInsert(jdbcTemplate)
                .withSchemaName(schemaName)
                .withTableName(tableName_instants)
                .usingGeneratedKeyColumns(field_id)
                .executeAndReturnKey(makeMapForRow(instant));
        return new InstantId(num.intValue());
    }

    private @NotNull Optional<Map<String, Instant>> retrieveById(@NotNull InstantId id) {
        final String sql = "SELECT * FROM "
                + fqTableName_instants
                + " WHERE "
                + field_id + " = ?";
        final List<Map<String, Instant>> list = jdbcTemplate.query(sql, this::mapRowToData, id.getRaw());
        if (list.isEmpty()) {
            return Optional.empty();
        } else {
            assert list.size() == 1;
            return Optional.of(list.get(0));
        }
    }

    private @NotNull Map<String, Instant> mapRowToData(@NotNull ResultSet row, int rowNum) throws SQLException {
        Map<String, Instant> res = new HashMap<>();
        {
            Timestamp timestamp = row.getTimestamp(field_instant_as_timestamp_with_tz_no_fractions);
            // getTime() returns the number of milliseconds "since the epoch", i.e. since 1970-01-01 00:00:00 GMT
            long secondsSinceEpoch = timestamp.getTime() / 1000;
            Instant when = Instant.ofEpochSecond(secondsSinceEpoch);
            res.put(field_instant_as_timestamp_with_tz_no_fractions, when);
        }
        {
            Timestamp timestamp = row.getTimestamp(field_instant_as_timestamp_with_tz);
            long secondsSinceEpoch = timestamp.getTime() / 1000;
            Instant when = Instant.ofEpochSecond(secondsSinceEpoch);
            res.put(field_instant_as_timestamp_with_tz, when);
        }
        {
            String raw = row.getString(field_instant_as_implicit_string);
            Instant when = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(raw));
            res.put(field_instant_as_implicit_string, when);
        }
        {
            String raw = row.getString(field_instant_as_explicit_string);
            Instant when = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(raw));
            res.put(field_instant_as_explicit_string, when);
        }
        {
            long raw = row.getLong(field_instant_as_seconds_since_epoch_explicit);
            Instant when = Instant.ofEpochSecond(raw);
            res.put(field_instant_as_seconds_since_epoch_explicit, when);
        }
        {
            Instant when = (Instant) (row.getObject(field_instant_as_java_object));
            res.put(field_instant_as_java_object, when);
        }
        {
            // retrieval "as what is really in the database", a local date and a local time
            LocalDate date = row.getDate(field_instant_as_explicit_date_utc).toLocalDate();
            LocalTime time = row.getTime(field_instant_as_explicit_time_utc).toLocalTime();
            // this seems to work:
            Instant when = Instant.ofEpochSecond(date.toEpochSecond(time, ZoneOffset.UTC));
            res.put("recombined_date_time", when);
        }
        return res;
    }

    // We will be testing a series of instant, starting here:

    private static Instant getStartInstant() {
        LocalDateTime when = LocalDateTime.of(2023, 8, 8, 12, 0);
        ZonedDateTime zdt = when.atZone(ZoneId.of("Europe/Luxembourg"));
        return Instant.from(zdt);
    }

    // We will be testing a series of instant, ending here:

    private static Instant getStopInstant() {
        LocalDateTime when = LocalDateTime.of(1990, 8, 8, 12, 0);
        ZonedDateTime zdt = when.atZone(ZoneId.of("Europe/Luxembourg"));
        return Instant.from(zdt);
    }

    // ---
    // The test!
    // If the database URL indicates a remote database, this can take a few seocnds
    // With an in-memory database, it is very fast
    // ---

    @Test
    void testInstantInsertionAndRetrieval() {
        setupDatabase(true);
        final Instant start = getStartInstant();
        final Instant stop = getStopInstant();
        Instant cur = start;
        while (cur.isAfter(stop)) {
            InstantId id = insert(cur);
            log.info("Testing {} {}, inserted as {}", Instant.class.getName(), cur, id);
            Optional<Map<String, Instant>> retrievedOpt = retrieveById(id);
            Assertions.assertTrue(retrievedOpt.isPresent());
            Map<String, Instant> retrieved = retrievedOpt.get();
            assertEquals(cur, retrieved.get(field_instant_as_timestamp_with_tz));
            assertEquals(cur, retrieved.get(field_instant_as_timestamp_with_tz_no_fractions));
            assertEquals(cur, retrieved.get(field_instant_as_java_object));
            assertEquals(cur, retrieved.get(field_instant_as_seconds_since_epoch_explicit));
            assertEquals(cur, retrieved.get(field_instant_as_seconds_since_epoch_explicit));
            assertEquals(cur, retrieved.get(field_instant_as_explicit_string));
            assertEquals(cur, retrieved.get(field_instant_as_implicit_string));
            assertEquals(cur, retrieved.get("recombined_date_time"));
            cur = cur.minus(Duration.ofDays(1));
        }

    }

}