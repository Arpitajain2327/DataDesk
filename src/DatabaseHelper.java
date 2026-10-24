import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper {
    private static final String URL = "jdbc:sqlite:edms.db";

    public DatabaseHelper() {
        try (Connection conn = DriverManager.getConnection(URL);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS departments ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL UNIQUE)");
            st.execute("CREATE TABLE IF NOT EXISTS employees ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL, "
                    + "email TEXT NOT NULL, "
                    + "salary REAL NOT NULL, "
                    + "role TEXT NOT NULL DEFAULT 'Developer', "
                    + "department_id INTEGER NOT NULL, "
                    + "FOREIGN KEY (department_id) REFERENCES departments(id))");
            st.execute("CREATE TABLE IF NOT EXISTS audit_log ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "username TEXT NOT NULL, "
                    + "action TEXT NOT NULL, "
                    + "details TEXT NOT NULL, "
                    + "created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')))");
            st.execute("CREATE TABLE IF NOT EXISTS leave_requests ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "employee_id INTEGER NOT NULL, "
                    + "leave_type TEXT NOT NULL, "
                    + "start_date TEXT NOT NULL, "
                    + "end_date TEXT NOT NULL, "
                    + "reason TEXT NOT NULL DEFAULT '', "
                    + "status TEXT NOT NULL DEFAULT 'Pending', "
                    + "decided_by TEXT NOT NULL DEFAULT '', "
                    + "created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')), "
                    + "FOREIGN KEY (employee_id) REFERENCES employees(id))");
        } catch (SQLException e) {
            System.out.println("Error creating tables: " + e.getMessage());
        }
        seedIfEmpty();
        seedUsers();
    }

    // Adds demo data only when the database is empty
    private void seedIfEmpty() {
        try (Connection conn = DriverManager.getConnection(URL);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM departments")) {
            if (rs.next() && rs.getInt(1) > 0) return;
        } catch (SQLException e) {
            return;
        }

        addDepartment("Engineering");
        addDepartment("HR");
        addDepartment("Sales");

        java.util.Map<String, Integer> ids = new java.util.HashMap<>();
        for (Department d : getDepartments()) ids.put(d.getName(), d.getId());

        addEmployee("Rohan Sharma", "rohan@example.com", 90000, ids.get("Engineering"), "Manager");
        addEmployee("Priya Verma", "priya@example.com", 65000, ids.get("Engineering"), "Developer");
        addEmployee("Aman Gupta", "aman@example.com", 60000, ids.get("Engineering"), "Developer");
        addEmployee("Neha Singh", "neha@example.com", 70000, ids.get("HR"), "Manager");
        addEmployee("Karan Mehta", "karan@example.com", 20000, ids.get("Sales"), "Intern");
    }

    // ---------- Departments ----------
    public boolean addDepartment(String name) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO departments(name) VALUES(?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Error adding department: " + e.getMessage());
            return false;
        }
    }

    public List<Department> getDepartments() {
        List<Department> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(URL);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name FROM departments ORDER BY name")) {
            while (rs.next()) {
                list.add(new Department(rs.getInt("id"), rs.getString("name")));
            }
        } catch (SQLException e) {
            System.out.println("Error reading departments: " + e.getMessage());
        }
        return list;
    }

    // Returns false if the department still has employees
    public boolean deleteDepartment(int id) {
        try (Connection conn = DriverManager.getConnection(URL)) {
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT COUNT(*) FROM employees WHERE department_id = ?")) {
                check.setInt(1, id);
                ResultSet rs = check.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) return false;
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM departments WHERE id = ?")) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.out.println("Error deleting department: " + e.getMessage());
            return false;
        }
    }

    // ---------- Employees ----------
    public boolean addEmployee(String name, String email, double salary, int departmentId, String role) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO employees(name, email, salary, department_id, role) VALUES(?,?,?,?,?)")) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setDouble(3, salary);
            ps.setInt(4, departmentId);
            ps.setString(5, role);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Error adding employee: " + e.getMessage());
            return false;
        }
    }

    public boolean updateEmployee(int id, String name, String email, double salary, int departmentId, String role) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE employees SET name=?, email=?, salary=?, department_id=?, role=? WHERE id=?")) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setDouble(3, salary);
            ps.setInt(4, departmentId);
            ps.setString(5, role);
            ps.setInt(6, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error updating employee: " + e.getMessage());
            return false;
        }
    }

    // Also removes the employee's leave requests so none are left orphaned
    public boolean deleteEmployee(int id) {
        try (Connection conn = DriverManager.getConnection(URL)) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM leave_requests WHERE employee_id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM employees WHERE id = ?")) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.out.println("Error deleting employee: " + e.getMessage());
            return false;
        }
    }

    // Search by name; departmentId 0 means "all departments"
    public List<Employee> searchEmployees(String query, int departmentId) {
        List<Employee> list = new ArrayList<>();
        String sql = "SELECT e.id, e.name, e.email, e.salary, e.role, e.department_id, d.name AS dept "
                + "FROM employees e LEFT JOIN departments d ON e.department_id = d.id "
                + "WHERE e.name LIKE ? AND (? = 0 OR e.department_id = ?) ORDER BY e.id";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + query + "%");
            ps.setInt(2, departmentId);
            ps.setInt(3, departmentId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(EmployeeFactory.create(rs.getString("role"),
                        rs.getInt("id"), rs.getString("name"), rs.getString("email"),
                        rs.getDouble("salary"), rs.getInt("department_id"), rs.getString("dept")));
            }
        } catch (SQLException e) {
            System.out.println("Error searching employees: " + e.getMessage());
        }
        return list;
    }

    public String getEmployeeName(int id) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement("SELECT name FROM employees WHERE id = ?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString(1);
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return "#" + id;
    }

    public boolean employeeExists(int id) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM employees WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
            return false;
        }
    }

    public String getDepartmentName(int id) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement("SELECT name FROM departments WHERE id = ?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString(1);
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return "#" + id;
    }

    // ---------- Leave requests ----------
    public boolean addLeave(int employeeId, String type, String start, String end, String reason) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO leave_requests(employee_id, leave_type, start_date, end_date, reason) "
                             + "VALUES(?,?,?,?,?)")) {
            ps.setInt(1, employeeId);
            ps.setString(2, type);
            ps.setString(3, start);
            ps.setString(4, end);
            ps.setString(5, reason);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Error adding leave: " + e.getMessage());
            return false;
        }
    }

    // True if the employee already has Pending or Approved leave overlapping these dates
    public boolean hasLeaveOverlap(int employeeId, String start, String end) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM leave_requests WHERE employee_id = ? "
                             + "AND status IN ('Pending','Approved') AND start_date <= ? AND end_date >= ?")) {
            ps.setInt(1, employeeId);
            ps.setString(2, end);
            ps.setString(3, start);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            System.out.println("Error checking leave overlap: " + e.getMessage());
            return false;
        }
    }

    // status "" means all statuses
    public List<LeaveRequest> getLeaves(String status) {
        List<LeaveRequest> list = new ArrayList<>();
        String sql = "SELECT l.id, l.employee_id, e.name AS emp, l.leave_type, l.start_date, l.end_date, "
                + "l.reason, l.status, l.decided_by, l.created_at "
                + "FROM leave_requests l LEFT JOIN employees e ON l.employee_id = e.id "
                + "WHERE (? = '' OR l.status = ?) ORDER BY l.id DESC";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, status);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new LeaveRequest(rs.getInt("id"), rs.getInt("employee_id"), rs.getString("emp"),
                        rs.getString("leave_type"), rs.getString("start_date"), rs.getString("end_date"),
                        rs.getString("reason"), rs.getString("status"), rs.getString("decided_by"),
                        rs.getString("created_at")));
            }
        } catch (SQLException e) {
            System.out.println("Error reading leaves: " + e.getMessage());
        }
        return list;
    }

    // Only a Pending request can be decided
    public boolean decideLeave(int id, String status, String decidedBy) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE leave_requests SET status = ?, decided_by = ? WHERE id = ? AND status = 'Pending'")) {
            ps.setString(1, status);
            ps.setString(2, decidedBy);
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error deciding leave: " + e.getMessage());
            return false;
        }
    }

    // ---------- Dashboard data ----------
    public String getStatsJson() {
        int totalEmployees = 0, totalDepartments = 0;
        double avgSalary = 0;
        StringBuilder perDept = new StringBuilder("[");
        try (Connection conn = DriverManager.getConnection(URL);
             Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT COUNT(*), IFNULL(AVG(salary), 0) FROM employees");
            if (rs.next()) {
                totalEmployees = rs.getInt(1);
                avgSalary = rs.getDouble(2);
            }
            rs = st.executeQuery("SELECT COUNT(*) FROM departments");
            if (rs.next()) totalDepartments = rs.getInt(1);

            rs = st.executeQuery("SELECT d.name, COUNT(e.id) AS cnt FROM departments d "
                    + "LEFT JOIN employees e ON e.department_id = d.id GROUP BY d.id ORDER BY d.name");
            boolean first = true;
            while (rs.next()) {
                if (!first) perDept.append(",");
                perDept.append("{\"name\":\"").append(Json.escape(rs.getString(1)))
                       .append("\",\"count\":").append(rs.getInt(2)).append("}");
                first = false;
            }
        } catch (SQLException e) {
            System.out.println("Error reading stats: " + e.getMessage());
        }
        perDept.append("]");
        return "{\"totalEmployees\":" + totalEmployees
                + ",\"totalDepartments\":" + totalDepartments
                + ",\"averageSalary\":" + Math.round(avgSalary)
                + ",\"perDepartment\":" + perDept + "}";
    }

    // ---------- Users ----------
    private void seedUsers() {
        try (Connection conn = DriverManager.getConnection(URL);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS users ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "username TEXT NOT NULL UNIQUE, "
                    + "password_hash TEXT NOT NULL, "
                    + "salt TEXT NOT NULL, "
                    + "role TEXT NOT NULL)");
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users");
            if (rs.next() && rs.getInt(1) > 0) return;
        } catch (SQLException e) {
            System.out.println("Error preparing users: " + e.getMessage());
            return;
        }
        addUser("admin", "admin123", "Admin");
        addUser("viewer", "viewer123", "Viewer");
    }

    public boolean addUser(String username, String password, String role) {
        String salt = PasswordUtil.newSalt();
        String hash = PasswordUtil.hash(password, salt);
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users(username, password_hash, salt, role) VALUES(?,?,?,?)")) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setString(3, salt);
            ps.setString(4, role);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Error adding user: " + e.getMessage());
            return false;
        }
    }

    // Returns the user if the password is correct, otherwise null
    public User authenticate(String username, String password) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, username, password_hash, salt, role FROM users WHERE username = ?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String calculated = PasswordUtil.hash(password, rs.getString("salt"));
                if (java.security.MessageDigest.isEqual(calculated.getBytes(),
                        rs.getString("password_hash").getBytes())) {
                    return new User(rs.getInt("id"), rs.getString("username"), rs.getString("role"));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error during login: " + e.getMessage());
        }
        return null;
    }

    // ---------- Audit log ----------
    public void addAudit(String username, String action, String details) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO audit_log(username, action, details) VALUES(?,?,?)")) {
            ps.setString(1, username);
            ps.setString(2, action);
            ps.setString(3, details);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error writing audit log: " + e.getMessage());
        }
    }

    public List<AuditEntry> getRecentAudit(int limit) {
        List<AuditEntry> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, username, action, details, created_at FROM audit_log ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new AuditEntry(rs.getInt("id"), rs.getString("username"),
                        rs.getString("action"), rs.getString("details"), rs.getString("created_at")));
            }
        } catch (SQLException e) {
            System.out.println("Error reading audit log: " + e.getMessage());
        }
        return list;
    }
}