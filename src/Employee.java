public abstract class Employee extends Person implements Jsonable {
    private int id;
    private double salary;
    private int departmentId;
    private String departmentName;

    public Employee(int id, String name, String email, double salary,
                    int departmentId, String departmentName) {
        super(name, email);
        this.id = id;
        this.salary = salary;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
    }

    public int getId() { return id; }
    public double getSalary() { return salary; }
    public int getDepartmentId() { return departmentId; }
    public String getDepartmentName() { return departmentName; }

    // Each subclass decides its own bonus percentage
    public abstract double getBonusRate();

    public double calculateBonus() {
        return salary * getBonusRate();
    }

    @Override
    public String toJson() {
        return "{\"id\":" + id
                + ",\"name\":\"" + Json.escape(getName()) + "\""
                + ",\"email\":\"" + Json.escape(getEmail()) + "\""
                + ",\"role\":\"" + getRole() + "\""
                + ",\"salary\":" + salary
                + ",\"bonus\":" + Math.round(calculateBonus())
                + ",\"departmentId\":" + departmentId
                + ",\"department\":\"" + Json.escape(departmentName) + "\"}";
    }
}
