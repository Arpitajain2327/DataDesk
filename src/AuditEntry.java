public class AuditEntry implements Jsonable {
    private int id;
    private String username;
    private String action;
    private String details;
    private String createdAt;

    public AuditEntry(int id, String username, String action, String details, String createdAt) {
        this.id = id;
        this.username = username;
        this.action = action;
        this.details = details;
        this.createdAt = createdAt;
    }

    @Override
    public String toJson() {
        return "{\"id\":" + id
                + ",\"username\":\"" + Json.escape(username) + "\""
                + ",\"action\":\"" + Json.escape(action) + "\""
                + ",\"details\":\"" + Json.escape(details) + "\""
                + ",\"createdAt\":\"" + Json.escape(createdAt) + "\"}";
    }
}