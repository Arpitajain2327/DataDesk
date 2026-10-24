public class User implements Jsonable {
    private int id;
    private String username;
    private String role;

    public User(int id, String username, String role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public boolean isAdmin() { return "Admin".equals(role); }

    @Override
    public String toJson() {
        return "{\"username\":\"" + Json.escape(username) + "\",\"role\":\"" + Json.escape(role) + "\"}";
    }
}
