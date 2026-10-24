import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {
    private final DatabaseHelper db;
    private final Map<String, User> sessions = new ConcurrentHashMap<>();

    public AuthService(DatabaseHelper db) {
        this.db = db;
    }

    // Returns a session token if the login is correct
    public String login(String username, String password) {
        if (username == null || password == null) {
            throw new UnauthorizedException("Invalid username or password");
        }
        User user = db.authenticate(username.trim(), password);
        if (user == null) {
            throw new UnauthorizedException("Invalid username or password");
        }
        String token = UUID.randomUUID().toString();
        sessions.put(token, user);
        return token;
    }

    public User getUser(String token) {
        return token == null ? null : sessions.get(token);
    }

    public void logout(String token) {
        if (token != null) sessions.remove(token);
    }
}
