import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private static final long SESSION_TTL_MS = 30 * 60 * 1000L;

    private final Map<String, Long> activeSessions = new ConcurrentHashMap<>();

    public String createSession() {
        purgeExpired();
        String sessionId = UUID.randomUUID().toString();
        activeSessions.put(sessionId, System.currentTimeMillis());
        return sessionId;
    }

    public boolean isValidSession(String sessionId) {
        Long lastSeen = activeSessions.get(sessionId);
        if (lastSeen == null) {
            return false;
        }
        if (System.currentTimeMillis() - lastSeen > SESSION_TTL_MS) {
            activeSessions.remove(sessionId);
            return false;
        }
        activeSessions.put(sessionId, System.currentTimeMillis());
        return true;
    }


    private void purgeExpired() {
        long now = System.currentTimeMillis();
        activeSessions.entrySet().removeIf(e -> now - e.getValue() > SESSION_TTL_MS);
    }
}
