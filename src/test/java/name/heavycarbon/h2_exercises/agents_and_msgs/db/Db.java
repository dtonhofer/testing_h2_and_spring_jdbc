package name.heavycarbon.h2_exercises.agents_and_msgs.db;

import name.heavycarbon.h2_exercises.agents_and_msgs.msg.*;
import name.heavycarbon.h2_exercises.agents_and_msgs.agent.AgentId;
import name.heavycarbon.h2_exercises.commons.DbHelpers;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

    public final static String schemaName = "testing_agents_and_msgs";

    // tn stands for "table name"
    public final static String tableName_message = "messages";

    // fq_tn stands for "fully qualified table name"
    public final static String fqTableName_message = String.format("%s.%s", schemaName, tableName_message);

    // "state_plain" exists just for usability if the user checks contents via console

    private final static String field_id = "id";
    private final static String field_state = "state";
    private final static String field_state_plain = "state_plain";
    private final static String field_sending_method = "sending_method";
    private final static String field_sender = "sender";
    private final static String field_receiver = "receiver";
    private final static String field_is_ack = "is_ack";
    private final static String field_acked_id = "acked_id";
    private final static String field_when_created = "when_created";
    private final static String field_when_acked = "when_acked";
    private final static String field_text = "text";

    // ----
    // Consult http://h2database.com/html/datatypes.html
    // Table creation
    // https://h2database.com/html/commands.html#create_table
    // Note the use of
    // https://h2database.com/html/datatypes.html#timestamp_with_time_zone_type
    // As we need to store an instant-in-time. Precision is milliseconds.
    // Text is VARCHAR (CHARACTER VARYING) so there is no superfluous whitespace
    // that needs to be trimmed, which is not the case for CHAR (CHARACTER).
    // The documentation says "too short strings are right-padded with space characters"
    //
    // Note that for real applications and large tables one would add a few indexes.
    // ----

    private void createTable() {
        final String sqlRaw = "CREATE TABLE IF NOT EXISTS "
                + fqTableName_message
                + " ("
                + field_id + " INTEGER AUTO_INCREMENT PRIMARY KEY, "
                + field_state + " INTEGER NOT NULL, "
                + field_state_plain + " VARCHAR(20) NOT NULL, " // state name; for easier manual debugging
                + field_sending_method + " VARCHAR(100) NOT NULL, " // register which Java method was used to insert; for manual debugging
                + field_sender + " INTEGER NOT NULL, "
                + field_receiver + " INTEGER NOT NULL, "
                + field_is_ack + " BOOLEAN NOT NULL, "
                + field_acked_id + " INTEGER DEFAULT NULL, " // set for "ack messages" but not for "true messages"
                + field_when_created + " TIMESTAMP(3) WITH TIME ZONE NOT NULL, " // meaningful "instant" demands timezone info!
                + field_when_acked + " TIMESTAMP(3) WITH TIME ZONE DEFAULT NULL, " // meaningful "instant" demands timezone info!
                + field_text + " VARCHAR(100) NOT NULL"
                + ")";
        jdbcTemplate.execute(sqlRaw);
    }

    // ---
    // "cleanup" indicates whether the tables and the schema should bd dropped when the
    // test finishes. If you run a volatile database (in-memory), there is no need to clean
    // up. If you want to inspect results later, you may not want to clean up.
    // ---

    public void setupDatabase(boolean cleanupFirst) {
        if (cleanupFirst) {
            DbHelpers.dropSchemaIfExists(schemaName, DbHelpers.Cascade.Yes, jdbcTemplate);
        }
        DbHelpers.createSchema(schemaName, jdbcTemplate);
        createTable();
    }

    // ---
    // Does what it says. Result should be assigned once to s static final.
    // ---

    private static String buildSql_CountMsgs() {
        return "SELECT COUNT(*) AS x FROM "
                + fqTableName_message
                + " WHERE "
                + field_receiver + " = ? "
                + " AND "
                + field_state + " = ?";
    }

    // ---
    // Count the messages sent to a certain agent, in a certain state
    // ---

    public int countMsgs(@NotNull AgentId receiver, @NotNull MsgState msgState) {
        final List<Integer> counts = jdbcTemplate.query(
                buildSql_CountMsgs(),
                Db::rowMapper_x,
                receiver.getRaw(),
                msgState.getRaw());
        assert counts.size() == 1;
        assert counts.get(0) >= 0;
        return counts.get(0);
    }

    private static Integer rowMapper_x(@NotNull ResultSet row, int rowNum) throws SQLException {
        return row.getInt("x");
    }

    private static Map<String, Object> makeMapForMsg(@NotNull Instant createdWhen, @NotNull AgentId sender, @NotNull AgentId receiver, @NotNull String text, @NotNull String sendingMethod) {
        final Map<String, Object> res = new HashMap<>();
        res.put(field_state, MsgState.fresh.getRaw());
        res.put(field_state_plain, MsgState.fresh.toString());
        res.put(field_sending_method, sendingMethod);
        res.put(field_sender, sender.getRaw());
        res.put(field_receiver, receiver.getRaw());
        res.put(field_is_ack, false);
        res.put(field_when_created, createdWhen); // Instant mapped "as is" to "TIMESTAMP WITH TIME ZONE". It works!
        res.put(field_text, text);
        return res;
    }

    // ---
    // Inserting a single "fresh" message.
    // Uses SimpleJdbcInsert, does not return id.
    // ---

    public void sendMsgWithSimpleJdbc(@NotNull Instant createdWhen, @NotNull AgentId sender, @NotNull AgentId receiver, @NotNull String text) {
        final Map<String, Object> values = Db.makeMapForMsg(createdWhen, sender, receiver, text, "sendMsgWithSimpleJdbc");
        final int count = new SimpleJdbcInsert(jdbcTemplate)
                .withSchemaName(schemaName)
                .withTableName(tableName_message)
                .usingGeneratedKeyColumns(field_id)
                .execute(values);
        if (count != 1) {
            throw new IllegalStateException("Insertion count is " + count + " instead of 1");
        }
    }

    // ---
    // Inserting a single "fresh" message.
    // Uses SimpleJdbcInsert, returns id.
    // ---

    public MsgId sendMsgWithSimpleJdbcReturningId(@NotNull Instant createdWhen, @NotNull AgentId sender, @NotNull AgentId receiver, @NotNull String text) {
        final Map<String, Object> values = Db.makeMapForMsg(createdWhen, sender, receiver, text, "sendMsgWithSimpleJdbcReturningId");
        final Number id = new SimpleJdbcInsert(jdbcTemplate)
                .withSchemaName(schemaName)
                .withTableName(tableName_message)
                .usingGeneratedKeyColumns(field_id)
                .executeAndReturnKey(values);
        return new MsgId(id.intValue());
    }

    // ---
    // Does what it says. Result should be assigned once to s static final.
    // ---

    private static String buildSql_sendMsg() {
        return "INSERT INTO "
                + fqTableName_message
                + " ("
                + field_state + ","
                + field_state_plain + ","
                + field_sending_method + ","
                + field_sender + ","
                + field_receiver + ","
                + field_is_ack + ","
                + field_when_created + ","
                + field_text
                + " ) "
                + " VALUES (?,?,?,?,?,?,?,?)";
    }

    // ---
    // Inserting a single "fresh" message.
    // Uses JDBCTemplate, does not return id.
    // ---

    public void sendMsgWithJdbcTemplate(@NotNull Instant createdWhen, @NotNull AgentId sender, @NotNull AgentId receiver, @NotNull String text) {
        final int count = jdbcTemplate.update(
                buildSql_sendMsg(),
                MsgState.fresh.getRaw(),
                MsgState.fresh.toString(),
                "sendMsgWithJdbcTemplate",
                sender.getRaw(),
                receiver.getRaw(),
                false,
                createdWhen, // Instant is correctly mapped to "TIMESTAMP WITH TIME ZONE" by driver. BIG WIN!
                text);
        if (count != 1) {
            throw new IllegalStateException("Insertion count is " + count + " instead of 1");
        }
    }

    // ---
    // Experiment with different ways of getting a "java.util.Instant" into the database.
    // ---

    private static void injectInstantExperimentally(int index, @NotNull Instant createdWhen, @NotNull PreparedStatement ps) throws SQLException {
        final int which = 2; // yeah, use this
        switch (which) {
            case 0 -> {
                // NOT WORKING PROPERLY!!
                // https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/Timestamp.html
                // https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/PreparedStatement.html#setTimestamp(int,java.sql.Timestamp,java.util.Calendar)
                // What happens here is that the instant is represented in the timezone given
                // E.g. the absolute instant "2023-08-09 09:18:52+00" is transformed into
                // local datetime "2023-08-09 03:18:52" ("America/Shiprock" timezone)
                // and then sent to the database with the default timezone ("Europe/Luxembourg")
                // which gives you "Europe/Luxembourg", giving the wrong
                // "2023-08-09 03:18:52+02"
                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/Shiprock"));
                ps.setTimestamp(index, Timestamp.from(createdWhen), cal);
            }
            case 1 -> {
                // Doing the roundabout way of passing a string actually works!
                ps.setString(index, DateTimeFormatter.ISO_INSTANT.format(createdWhen));
            }
            case 2 -> {
                // Directly setting an "Instant" object works too. BIG WIN. Excellent.
                ps.setObject(index, createdWhen);
            }
        }
    }

    // ---
    // Inserting a single "fresh" message.
    // Uses JDBCTemplate, does not return id.
    // We also experiment with different ways of getting a "java.util.Instant" into the database.
    // ---

    public MsgId sendMsgWithJdbcTemplateReturningId(@NotNull Instant createdWhen, @NotNull AgentId sender, @NotNull AgentId receiver, @NotNull String text) {
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        int count = jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(buildSql_sendMsg(), Statement.RETURN_GENERATED_KEYS);
            int index = 1; // start at 1 not 0
            ps.setInt(index++, MsgState.fresh.getRaw());
            ps.setString(index++, MsgState.fresh.toString());
            ps.setString(index++, "sendMsgWithJdbcTemplateReturningId");
            ps.setInt(index++, sender.getRaw());
            ps.setInt(index++, receiver.getRaw());
            ps.setBoolean(index++, false); // "not an ACK"
            // >>> trying various things!
            injectInstantExperimentally(index++, createdWhen, ps);
            // <<<
            ps.setString(index, text);
            return ps;
        }, keyHolder);
        if (count != 1) {
            throw new IllegalStateException("Insertion count is " + count + " instead of 1");
        }
        final Integer rawKey = keyHolder.getKeyAs(Integer.class);
        if (rawKey == null) {
            throw new IllegalStateException("Did not obtain a valid key");
        }
        return new MsgId(rawKey);
    }

    // ---
    // Transforming raw data from a ResultSet to an actual message instance.
    // This method maps to a Spring "RowMapper" functional interface.
    // https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/jdbc/core/RowMapper.html
    // ---

    private static MsgBase rowMapper_msg(@NotNull ResultSet row, int rowNum) throws SQLException {
        final MsgId msgId = new MsgId(row.getInt(field_id));
        final MsgState msgState = MsgState.byCode(row.getInt(field_state));
        final AgentId sender = new AgentId(row.getInt(field_sender));
        final AgentId receiver = new AgentId(row.getInt(field_receiver));
        final boolean isAck = row.getBoolean(field_is_ack);
        if (isAck) {
            final MsgId ackedId = new MsgId(row.getInt(field_acked_id));
            return new AckMsg(msgId, msgState, sender, receiver, ackedId);
        } else {
            // "text" is a VARCHAR so there is no superfluous whitespace that needs to be trimmed!
            // This is not the case with CHAR.
            final String text = row.getString(field_text);
            return new TrueMsg(msgId, msgState, sender, receiver, text);
        }
    }

    // ---
    // Does what it says. Result should be assigned once to s static final.
    // ---

    private static String buildSql_retrieveMsgs() {
        return "SELECT "
                + field_id + ","
                + field_state + ","
                + field_sender + ","
                + field_receiver + ","
                + field_is_ack + ","
                + field_acked_id + ","
                + field_text
                + " FROM "
                + fqTableName_message
                + " WHERE "
                + field_receiver + " = ? "
                + " AND "
                + field_state + " = ? "
                + " ORDER BY "
                + field_when_created
                + " ASC";
    }

    // ---
    // Querying the message table.
    // Query writing convention when on several lines: whitespace on both ends to be sure there is some!
    // ---

    public List<MsgBase> retrieveMsgs(@NotNull AgentId receiver, @NotNull MsgState msgState) {
        // "state" and "receiver" fields are in the result, so no need to pass then around separately
        return jdbcTemplate.query(buildSql_retrieveMsgs(), Db::rowMapper_msg, receiver.getRaw(), msgState.getRaw());
    }

    // ---
    // Does what it says. Result should be assigned once to s static final.
    // ---

    private static String buildSql_ackMsg() {
        return "UPDATE "
                + fqTableName_message
                + " SET "
                + field_state + " = ?, "
                + field_state_plain + " = ?, "
                + field_when_acked + " = ? "
                + " WHERE "
                + field_id + " = ? "
                + " AND "
                + field_state + " = ? "
                + " AND "
                + field_receiver + " = ?";
    }

    // ---
    // Changing the state of a message to "SEEN".
    // Also includes "state" and "receiver" in the WHERE for additional insurance
    // (it should not be necessary!)
    // ---

    public void markMsgAsSeen(@NotNull MsgId msgId, @NotNull Instant ackedWhen, @NotNull AgentId receiver) {
        final int count = jdbcTemplate.update(
                buildSql_ackMsg(),
                MsgState.seen.getRaw(), // SET part
                MsgState.seen.toString(), // SET part
                ackedWhen, // SET part
                msgId.getRaw(), // WHERE part
                MsgState.fresh.getRaw(), // WHERE part
                receiver.getRaw()); // WHERE part
        if (count != 1) {
            throw new IllegalStateException("Trying to acknowledge " + msgId + " resulted in a count of " + count + " instead of 1");
        }
    }

    // ---
    // Does what it says
    // ---

    private static Map<String, Object> buildMap_ackMsg(@NotNull AgentId sender, @NotNull TrueMsg forTrueMsg, @NotNull Instant createdWhen, @NotNull String sendingMethod) {
        final Map<String, Object> res = new HashMap<>();
        res.put(field_state, MsgState.fresh.getRaw());
        res.put(field_state_plain, MsgState.fresh.toString());
        res.put(field_sending_method, sendingMethod);
        res.put(field_sender, sender.getRaw());
        assert sender.equals(forTrueMsg.getReceiver());
        res.put(field_receiver, forTrueMsg.getSender().getRaw());
        res.put(field_is_ack, true);
        res.put(field_acked_id, forTrueMsg.getId().getRaw()); // we "ack" the "forTrueMsg", so its "id" goes to "acked_id"
        res.put(field_when_created, createdWhen);
        res.put(field_text, "ACK " + forTrueMsg.getId() + ", '" + forTrueMsg.getText() + "'"); // text of no interest, but let's test the single quotes
        return res;
    }

    // ---
    // Sending an acknowledgment message to the agent registered as "sender" in the "TrueMsg".
    // The sender of the acknowledgment message is "sender". The new record's creation date
    // will be "createdWhen".
    // ---

    public MsgId sendAckMsgReturningId(@NotNull AgentId sender, @NotNull TrueMsg forTrueMsg, @NotNull Instant createdWhen) {
        final Map<String, Object> values = buildMap_ackMsg(sender, forTrueMsg, createdWhen, "sendAckMsgReturningId");
        final Number id = new SimpleJdbcInsert(jdbcTemplate)
                .withSchemaName(schemaName)
                .withTableName(tableName_message)
                .usingGeneratedKeyColumns(field_id)
                .executeAndReturnKey(values);
        return new MsgId(id.intValue());
    }

}
