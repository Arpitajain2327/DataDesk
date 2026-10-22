public class Department implements Jsonable { 
    private int id;
    private String name;

    public Department(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    @Override 
    public String toJson() {
        return "{\"id\":" + id + ",\"name\":\"" + Json.escape(name) + "\"}";
    }
}