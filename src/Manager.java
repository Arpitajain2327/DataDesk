public class Manager extends Employee {
    public Manager(int id, String name, String email, double salary,
                   int departmentId, String departmentName) {
        super(id, name, email, salary, departmentId, departmentName);
    }

    @Override
    public String getRole() { return "Manager"; }

    @Override
    public double getBonusRate() { return 0.20; }
}
