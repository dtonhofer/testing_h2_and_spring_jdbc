package name.heavycarbon.h2_exercises.transactions.db;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.util.List;

@Slf4j
@Component
public class SessionManip {

    private final @NotNull JdbcTemplate jdbcTemplate;

    @Autowired
    public SessionManip(@NotNull JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---
    // Query to see the structure of INFORMATION_SCHEMA.SESSIONS.
    // - SESSION_ID is an INTEGER (java.lang.Integer)
    // - ISOLATION_LEVEL is a CHARACTER_VARYING (we find for example "SERIALIZABLE")
    // ---

    public @NotNull SessionInfo getMySessionInfo() {
        final String sql = "SELECT session_id, isolation_level "
                + " FROM information_schema.sessions "
                + " WHERE session_id = SESSION_ID()";
        List<SessionInfo> list =
                jdbcTemplate.query(sql,
                        (@NotNull ResultSet row, int rowNum) -> {
                            SessionId id = new SessionId(row.getInt(1));
                            Isol il = Isol.fromString(row.getString(2));
                            return new SessionInfo(id, il);
                        });
        return list.get(0);
    }

    // ---
    // Query all sessions, also mark "my session" specially.
    // Returns the sessions as a list sorted by session-id ascending.
    // ---

    public @NotNull List<SessionInfoExt> getAllSessionsInfo() {
        final String sql = "SELECT session_id, isolation_level, session_id = SESSION_ID() AS is_my_session "
                + " FROM information_schema.sessions "
                + " ORDER BY session_id";
        return
                jdbcTemplate.query(sql,
                        (@NotNull ResultSet row, int rowNum) -> {
                            SessionId id = new SessionId(row.getInt(1));
                            Isol il = Isol.fromString(row.getString(2));
                            boolean isMySession = row.getBoolean(3);
                            return new SessionInfoExt(id, il, isMySession);
                        });
    }

    // ---
    // We use the H2 command to set the isolation level
    // http://h2database.com/html/commands.html#set_session_characteristics
    // ---

    public void setMySessionIsolationLevel(@NotNull Isol level) {
        jdbcTemplate.execute("SET SESSION CHARACTERISTICS AS TRANSACTION ISOLATION LEVEL " + Isol.toSql(level));
    }

    // ---
    // Used to inspect current session
    // ---

    public static String printAllSessions(@NotNull SessionManip sm) {
        List<SessionInfoExt> list = sm.getAllSessionsInfo();
        StringBuilder buf = new StringBuilder("Sessions");
        for (SessionInfoExt iext : list) {
            buf.append("\n");
            buf.append("   ");
            buf.append(iext.sessionId());
            buf.append(", ");
            buf.append(iext.isol());
            if (iext.isMySession()) {
                buf.append(", mine");
            }
        }
        return buf.toString();
    }
}
