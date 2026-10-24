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
    private static final int PORT = System.getenv("PORT") != null
            ? Integer.parseInt(System.getenv("PORT")) : 8081;

    private static final DatabaseHelper db = new DatabaseHelper();
    private static final EmployeeService employeeService = new EmployeeService(db);
    private static final DepartmentService departmentService = new DepartmentService(db);
    private static final LeaveService leaveService = new LeaveService(db);
    private static final AuthService auth = new AuthService(db);
    private static final Path WEB_DIR = Paths.get("web").toAbsolutePath().normalize();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", App::handle);
        server.start();
        System.out.println("Server started at http://localhost:" + PORT);
    }

    // ---------- Router (controller layer) ----------
    private static void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();

            Map<String, String> params = parse(ex.getRequestURI().getRawQuery());
            if (method.equals("POST") || method.equals("PUT")) {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                params.putAll(parse(body));
            }

            // Web pages are public; the data behind /api/ is protected
            if (!path.startsWith("/api/")) {
                serveFile(ex, path);
                return;
            }

            // Login is the only open API route
            if (path.equals("/api/login") && method.equals("POST")) {
                String token = auth.login(params.get("username"), params.get("password"));
                ex.getResponseHeaders().add("Set-Cookie",
                        "SESSION=" + token + "; Path=/; HttpOnly; SameSite=Strict");
                send(ex, 200, "application/json", auth.getUser(token).toJson());
                return;
            }

            String token = getToken(ex);
            User user = auth.getUser(token);
            if (user == null) {
                throw new UnauthorizedException("Please log in");
            }

            if (path.equals("/api/me")) {
                send(ex, 200, "application/json", user.toJson());
                return;
            }
            if (path.equals("/api/logout")) {
                auth.logout(token);
                ex.getResponseHeaders().add("Set-Cookie", "SESSION=; Path=/; Max-Age=0");
                send(ex, 200, "application/json", "{\"ok\":true}");
                return;
            }

            // Only admins can change data
            if (!method.equals("GET") && !user.isAdmin()) {
                throw new ForbiddenException("Only admins can change data");
            }

            if (path.equals("/api/departments")) {
                departments(ex, method, params, user);
            } else if (path.equals("/api/employees")) {
                employees(ex, method, params, user);
            } else if (path.equals("/api/leaves")) {
                leaves(ex, method, params, user);
            } else if (path.equals("/api/activity") && method.equals("GET")) {
                if (!user.isAdmin()) throw new ForbiddenException("Only admins can view activity");
                send(ex, 200, "application/json", toJsonArray(departmentService.recentActivity()));
            } else if (path.equals("/api/stats") && method.equals("GET")) {
                send(ex, 200, "application/json", employeeService.statsJson());
            } else if (path.equals("/api/export") && method.equals("GET")) {
                export(ex, params);
            } else {
                send(ex, 404, "application/json", "{\"error\":\"Not found\"}");
            }
        } catch (UnauthorizedException e) {
            send(ex, 401, "application/json", "{\"error\":\"" + Json.escape(e.getMessage()) + "\"}");
        } catch (ForbiddenException e) {
            send(ex, 403, "application/json", "{\"error\":\"" + Json.escape(e.getMessage()) + "\"}");
        } catch (ValidationException e) {
            send(ex, 400, "application/json", "{\"error\":\"" + Json.escape(e.getMessage()) + "\"}");
        } catch (NotFoundException e) {
            send(ex, 404, "application/json", "{\"error\":\"" + Json.escape(e.getMessage()) + "\"}");
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, "application/json", "{\"error\":\"Server error\"}");
        }
    }

    // Reads the SESSION cookie sent by the browser
    private static String getToken(HttpExchange ex) {
        String cookies = ex.getRequestHeaders().getFirst("Cookie");
        if (cookies == null) return null;
        for (String c : cookies.split(";")) {
            String[] kv = c.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals("SESSION")) return kv[1];
        }
        return null;
    }

    // ---------- Departments ----------
    private static void departments(HttpExchange ex, String method, Map<String, String> p, User user) throws IOException {
        switch (method) {
            case "GET":
                send(ex, 200, "application/json", toJsonArray(departmentService.list()));
                break;
            case "POST":
                departmentService.add(user.getUsername(), p.getOrDefault("name", ""));
                send(ex, 200, "application/json", "{\"ok\":true}");
                break;
            case "DELETE":
                departmentService.delete(user.getUsername(), toInt(p.get("id"), -1));
                send(ex, 200, "application/json", "{\"ok\":true}");
                break;
            default:
                send(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}");
        }
    }

    // ---------- Employees ----------
    private static void employees(HttpExchange ex, String method, Map<String, String> p, User user) throws IOException {
        switch (method) {
            case "GET":
                send(ex, 200, "application/json",
                        toJsonArray(employeeService.search(p.getOrDefault("q", ""), toInt(p.get("dept"), 0))));
                break;
            case "POST":
                employeeService.add(user.getUsername(), p.getOrDefault("name", ""), p.getOrDefault("email", ""),
                        p.getOrDefault("salary", ""), p.getOrDefault("role", "").trim(),
                        toInt(p.get("departmentId"), 0));
                send(ex, 200, "application/json", "{\"ok\":true}");
                break;
            case "PUT":
                employeeService.update(user.getUsername(), toInt(p.get("id"), -1), p.getOrDefault("name", ""),
                        p.getOrDefault("email", ""), p.getOrDefault("salary", ""),
                        p.getOrDefault("role", "").trim(), toInt(p.get("departmentId"), 0));
                send(ex, 200, "application/json", "{\"ok\":true}");
                break;
            case "DELETE":
                employeeService.delete(user.getUsername(), toInt(p.get("id"), -1));
                send(ex, 200, "application/json", "{\"ok\":true}");
                break;
            default:
                send(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}");
        }
    }

    // ---------- Leaves ----------
    private static void leaves(HttpExchange ex, String method, Map<String, String> p, User user) throws IOException {
        switch (method) {
            case "GET":
                send(ex, 200, "application/json", toJsonArray(leaveService.list(p.getOrDefault("status", ""))));
                break;
            case "POST":
                leaveService.apply(user.getUsername(), toInt(p.get("employeeId"), 0),
                        p.getOrDefault("leaveType", ""), p.getOrDefault("startDate", ""),
                        p.getOrDefault("endDate", ""), p.getOrDefault("reason", ""));
                send(ex, 200, "application/json", "{\"ok\":true}");
                break;
            case "PUT":
                leaveService.decide(user.getUsername(), toInt(p.get("id"), -1), p.getOrDefault("status", ""));
                send(ex, 200, "application/json", "{\"ok\":true}");
                break;
            default:
                send(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}");
        }
    }

    // ---------- CSV export ----------
    private static void export(HttpExchange ex, Map<String, String> p) throws IOException {
        List<Employee> list = employeeService.search(p.getOrDefault("q", ""), toInt(p.get("dept"), 0));
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

    // ---------- Serve the web pages ----------
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