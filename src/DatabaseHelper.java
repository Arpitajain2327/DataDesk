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
        } catch (SQLException e) {
            System.out.println("Error creating tables: " + e.getMessage());
        }
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

    public boolean deleteEmployee(int id) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement("DELETE FROM employees WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
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
                // The factory decides which subclass to create
                list.add(EmployeeFactory.create(rs.getString("role"),
                        rs.getInt("id"), rs.getString("name"), rs.getString("email"),
                        rs.getDouble("salary"), rs.getInt("department_id"), rs.getString("dept")));
            }
        } catch (SQLException e) {
            System.out.println("Error searching employees: " + e.getMessage());
        }
        return list;
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
}
