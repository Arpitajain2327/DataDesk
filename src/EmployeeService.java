import java.util.List;

public class EmployeeService {
    private final DatabaseHelper db;

    public EmployeeService(DatabaseHelper db) {
        this.db = db;
    }

    public List<Employee> search(String query, int departmentId) {
        return db.searchEmployees(query == null ? "" : query, departmentId);
    }

    public void add(String actor, String name, String email, String salaryText, String role, int departmentId) {
        double salary = validate(name, email, salaryText, role, departmentId);
        if (!db.addEmployee(name.trim(), email.trim(), salary, departmentId, role)) {
            throw new ValidationException("Could not save employee");
        }
        db.addAudit(actor, "ADD_EMPLOYEE", "Added " + name.trim() + " as " + role);
    }

    public void update(String actor, int id, String name, String email, String salaryText, String role, int departmentId) {
        double salary = validate(name, email, salaryText, role, departmentId);
        if (!db.updateEmployee(id, name.trim(), email.trim(), salary, departmentId, role)) {
            throw new NotFoundException("Employee not found");
        }
        db.addAudit(actor, "UPDATE_EMPLOYEE", "Updated " + name.trim() + " (id " + id + ")");
    }

    public void delete(String actor, int id) {
        String name = db.getEmployeeName(id);
        if (!db.deleteEmployee(id)) {
            throw new NotFoundException("Employee not found");
        }
        db.addAudit(actor, "DELETE_EMPLOYEE", "Deleted " + name);
    }

    public String statsJson() {
        return db.getStatsJson();
    }

    private double validate(String name, String email, String salaryText, String role, int departmentId) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Name is required");
        }
        if (email == null || !email.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ValidationException("Please enter a valid email");
        }
        double salary;
        try {
            salary = Double.parseDouble(salaryText);
        } catch (Exception e) {
            throw new ValidationException("Salary must be a number");
        }
        if (salary < 0) {
            throw new ValidationException("Salary cannot be negative");
        }
        if (!EmployeeFactory.isValidRole(role)) {
            throw new ValidationException("Role must be Manager, Developer or Intern");
        }
        if (departmentId <= 0) {
            throw new ValidationException("Please select a department");
        }
        return salary;
    }
}