public class Intern extends Employee {
    public Intern(int id, String name, String email, double salary,
                  int departmentId, String departmentName) {
        super(id, name, email, salary, departmentId, departmentName);
    }

    @Override
    public String getRole() { return "Intern"; }

    @Override
    public double getBonusRate() { return 0.05; }
}
