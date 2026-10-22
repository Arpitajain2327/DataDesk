public class Developer extends Employee {
    public Developer(int id, String name, String email, double salary,
                     int departmentId, String departmentName) {
        super(id, name, email, salary, departmentId, departmentName);
    }

    @Override
    public String getRole() { return "Developer"; }

    @Override
    public double getBonusRate() { return 0.10; }
}
