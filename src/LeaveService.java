import java.time.LocalDate;
import java.util.List;

public class LeaveService {
    private final DatabaseHelper db;

    public LeaveService(DatabaseHelper db) {
        this.db = db;
    }

    public List<LeaveRequest> list(String status) {
        return db.getLeaves(status == null ? "" : status);
    }

    public void apply(String actor, int employeeId, String type, String start, String end, String reason) {
        if (!db.employeeExists(employeeId)) {
            throw new ValidationException("Please select an employee");
        }
        if (!type.equals("Casual") && !type.equals("Sick") && !type.equals("Earned")) {
            throw new ValidationException("Leave type must be Casual, Sick or Earned");
        }
        LocalDate startDate, endDate;
        try {
            startDate = LocalDate.parse(start);
            endDate = LocalDate.parse(end);
        } catch (Exception e) {
            throw new ValidationException("Please enter valid start and end dates");
        }
        if (endDate.isBefore(startDate)) {
            throw new ValidationException("End date cannot be before start date");
        }
        if (db.hasLeaveOverlap(employeeId, start, end)) {
            throw new ValidationException("This employee already has leave in those dates");
        }
        String cleanReason = reason == null ? "" : reason.trim();
        if (!db.addLeave(employeeId, type, start, end, cleanReason)) {
            throw new ValidationException("Could not save leave request");
        }
        db.addAudit(actor, "APPLY_LEAVE", type + " leave for " + db.getEmployeeName(employeeId)
                + " (" + start + " to " + end + ")");
    }

    public void decide(String actor, int id, String status) {
        if (!status.equals("Approved") && !status.equals("Rejected")) {
            throw new ValidationException("Status must be Approved or Rejected");
        }
        if (!db.decideLeave(id, status, actor)) {
            throw new ValidationException("Only pending requests can be decided");
        }
        db.addAudit(actor, status.equals("Approved") ? "APPROVE_LEAVE" : "REJECT_LEAVE",
                status + " leave request #" + id);
    }
}