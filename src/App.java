import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class App {
    private static final int PORT = 8081;
    private static final DatabaseHelper db = new DatabaseHelper();
    private static final Path WEB_DIR = Paths.get("web").toAbsolutePath().normalize();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", App::handle);
        server.start();
        System.out.println("Server started at http://localhost:" + PORT);
    }

    // ---------- Router ----------
    private static void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();

            Map<String, String> params = parse(ex.getRequestURI().getRawQuery());
            if (method.equals("POST") || method.equals("PUT")) {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                params.putAll(parse(body));
            }

            if (path.equals("/api/departments")) {
                departments(ex, method, params);
            } else if (path.equals("/api/employees")) {
                employees(ex, method, params);
            } else if (path.equals("/api/stats") && method.equals("GET")) {
                send(ex, 200, "application/json", db.getStatsJson());
            } else if (path.equals("/api/export") && method.equals("GET")) {
                export(ex, params);
            } else if (path.startsWith("/api/")) {
                send(ex, 404, "application/json", "{\"error\":\"Not found\"}");
            } else {
                serveFile(ex, path);
            }
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, "application/json", "{\"error\":\"Server error\"}");
        }
    }

    // ---------- Departments ----------
    private static void departments(HttpExchange ex, String method, Map<String, String> p) throws IOException {
        switch (method) {
            case "GET":
                send(ex, 200, "application/json", toJsonArray(db.getDepartments()));
                break;
            case "POST": {
                String name = p.getOrDefault("name", "").trim();
                if (name.isEmpty()) {
                    send(ex, 400, "application/json", "{\"error\":\"Department name is required\"}");
                } else if (db.addDepartment(name)) {
                    send(ex, 200, "application/json", "{\"ok\":true}");
                } else {
                    send(ex, 400, "application/json", "{\"error\":\"Department already exists\"}");
                }
                break;
            }
            case "DELETE": {
                int id = toInt(p.get("id"), -1);
                if (db.deleteDepartment(id)) {
                    send(ex, 200, "application/json", "{\"ok\":true}");
                } else {
                    send(ex, 409, "application/json",
                            "{\"error\":\"Cannot delete: department has employees or does not exist\"}");
                }
                break;
            }
            default:
                send(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}");
        }
    }

    // ---------- Employees ----------
    private static void employees(HttpExchange ex, String method, Map<String, String> p) throws IOException {
        switch (method) {
            case "GET":
                send(ex, 200, "application/json",
                        toJsonArray(db.searchEmployees(p.getOrDefault("q", ""), toInt(p.get("dept"), 0))));
                break;
            case "POST":
            case "PUT": {
                String name = p.getOrDefault("name", "").trim();
                String email = p.getOrDefault("email", "").trim();
                String role = p.getOrDefault("role", "").trim();
                int deptId = toInt(p.get("departmentId"), 0);
                double salary;
                try {
                    salary = Double.parseDouble(p.getOrDefault("salary", ""));
                } catch (NumberFormatException e) {
                    send(ex, 400, "application/json", "{\"error\":\"Salary must be a number\"}");
                    return;
                }
                if (name.isEmpty() || email.isEmpty() || deptId <= 0 || salary < 0
                        || !EmployeeFactory.isValidRole(role)) {
                    send(ex, 400, "application/json", "{\"error\":\"Please fill all fields correctly\"}");
                    return;
                }
                boolean ok = method.equals("POST")
                        ? db.addEmployee(name, email, salary, deptId, role)
                        : db.updateEmployee(toInt(p.get("id"), -1), name, email, salary, deptId, role);
                send(ex, ok ? 200 : 400, "application/json",
                        ok ? "{\"ok\":true}" : "{\"error\":\"Could not save employee\"}");
                break;
            }
            case "DELETE": {
                boolean ok = db.deleteEmployee(toInt(p.get("id"), -1));
                send(ex, ok ? 200 : 404, "application/json",
                        ok ? "{\"ok\":true}" : "{\"error\":\"Employee not found\"}");
                break;
            }
            default:
                send(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}");
        }
    }

    // ---------- CSV export ----------
    private static void export(HttpExchange ex, Map<String, String> p) throws IOException {
        List<Employee> list = db.searchEmployees(p.getOrDefault("q", ""), toInt(p.get("dept"), 0));
        StringBuilder sb = new StringBuilder("ID,Name,Email,Role,Department,Salary,Bonus\n");
        for (Employee e : list) {
            sb.append(e.getId()).append(",")
              .append(csv(e.getName())).append(",")
              .append(csv(e.getEmail())).append(",")
              .append(e.getRole()).append(",")
              .append(csv(e.getDepartmentName())).append(",")
              .append(e.getSalary()).append(",")
              .append(Math.round(e.calculateBonus())).append("\n");
        }
        ex.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"employees.csv\"");
        send(ex, 200, "text/csv", sb.toString());
    }

    private static String csv(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // ---------- Serve the web page ----------
    private static void serveFile(HttpExchange ex, String path) throws IOException {
        if (path.equals("/")) path = "/index.html";
        Path file = WEB_DIR.resolve(path.substring(1)).normalize();

        if (!file.startsWith(WEB_DIR) || !Files.isRegularFile(file)) {
            send(ex, 404, "text/plain", "Page not found");
            return;
        }
        String name = file.getFileName().toString();
        String type = name.endsWith(".html") ? "text/html"
                : name.endsWith(".css") ? "text/css"
                : name.endsWith(".js") ? "application/javascript"
                : "application/octet-stream";
        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ---------- Helpers ----------
    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // Works for any list of Jsonable objects (employees, departments...)
    private static String toJsonArray(List<? extends Jsonable> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(items.get(i).toJson());
        }
        return sb.append("]").toString();
    }

    private static Map<String, String> parse(String s) {
        Map<String, String> map = new HashMap<>();
        if (s == null || s.isEmpty()) return map;
        for (String pair : s.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String val = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(key, val);
        }
        return map;
    }

    private static int toInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return fallback;
        }
    }
}
