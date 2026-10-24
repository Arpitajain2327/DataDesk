import java.util.List;

public class DepartmentService {
    private final DatabaseHelper db;

    public DepartmentService(DatabaseHelper db) {
        this.db = db;
    }

    public List<Department> list() {
        return db.getDepartments();
    }

    public void add(String actor, String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Department name is required");
        }
        if (!db.addDepartment(name.trim())) {
            throw new ValidationException("Department already exists");
        }
        db.addAudit(actor, "ADD_DEPARTMENT", "Added department " + name.trim());
    }

    public void delete(String actor, int id) {
        String name = db.getDepartmentName(id);
        if (!db.deleteDepartment(id)) {
            throw new ValidationException("Cannot delete: department has employees or does not exist");
        }
        db.addAudit(actor, "DELETE_DEPARTMENT", "Deleted department " + name);
    }

    public List<AuditEntry> recentActivity() {
        return db.getRecentAudit(50);
    }
}