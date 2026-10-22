public class EmployeeFactory {

    public static boolean isValidRole(String role) {
        return role.equals("Manager") || role.equals("Developer") || role.equals("Intern");
    }

    public static Employee create(String role, int id, String name, String email,
                                  double salary, int departmentId, String departmentName) {
        switch (role) {
            case "Manager":
                return new Manager(id, name, email, salary, departmentId, departmentName);
            case "Intern":
                return new Intern(id, name, email, salary, departmentId, departmentName);
            default:
                return new Developer(id, name, email, salary, departmentId, departmentName);
        }
    }
}
