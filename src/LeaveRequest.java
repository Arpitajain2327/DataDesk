import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class LeaveRequest implements Jsonable {
    private int id;
    private int employeeId;
    private String employeeName;
    private String leaveType;
    private String startDate;
    private String endDate;
    private String reason;
    private String status;
    private String decidedBy;
    private String createdAt;

    public LeaveRequest(int id, int employeeId, String employeeName, String leaveType,
                        String startDate, String endDate, String reason,
                        String status, String decidedBy, String createdAt) {
        this.id = id;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.status = status;
        this.decidedBy = decidedBy;
        this.createdAt = createdAt;
    }

    public long getDays() {
        return ChronoUnit.DAYS.between(LocalDate.parse(startDate), LocalDate.parse(endDate)) + 1;
    }

    @Override
    public String toJson() {
        return "{\"id\":" + id
                + ",\"employeeId\":" + employeeId
                + ",\"employee\":\"" + Json.escape(employeeName == null ? "(deleted)" : employeeName) + "\""
                + ",\"leaveType\":\"" + Json.escape(leaveType) + "\""
                + ",\"startDate\":\"" + Json.escape(startDate) + "\""
                + ",\"endDate\":\"" + Json.escape(endDate) + "\""
                + ",\"days\":" + getDays()
                + ",\"reason\":\"" + Json.escape(reason) + "\""
                + ",\"status\":\"" + Json.escape(status) + "\""
                + ",\"decidedBy\":\"" + Json.escape(decidedBy) + "\""
                + ",\"createdAt\":\"" + Json.escape(createdAt) + "\"}";
    }
}
