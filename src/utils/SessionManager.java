
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SessionManager {
    private final Set<String> activeSessions = new HashSet<>();

    public String createSession() {
        String sessionId = UUID.randomUUID().toString();
        activeSessions.add(sessionId);
        return sessionId;
    }

    public boolean isValidSession(String sessionId) {
        return activeSessions.contains(sessionId);
    }
}