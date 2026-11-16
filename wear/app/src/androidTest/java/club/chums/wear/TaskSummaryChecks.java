package club.chums.wear;

import org.json.JSONObject;

final class TaskSummaryChecks {
    static void run() throws Exception {
        JSONObject state = new JSONObject("{\"agents\":[{\"id\":\"noodle\",\"status\":\"working\",\"line\":null},{\"id\":\"squint\",\"status\":\"waiting\"}],\"spoken\":[{\"agent\":\"noodle\",\"kind\":\"echo\",\"text\":\"Fix tests\"},{\"agent\":\"squint\",\"kind\":\"echo\",\"text\":\"Other task\"}],\"approvals\":[{\"session\":\"squint\",\"description\":\"Run command\"}]}");
        TaskSummary task = TaskSummary.from(state, "noodle", true);
        check(task.status.equals("Working") && task.title.equals("Fix tests") && task.detail.isEmpty());
        task = TaskSummary.from(state, "squint", true);
        check(task.status.equals("Needs approval") && task.detail.equals("Run command"));
        check(TaskSummary.from(state, "noodle", false).status.startsWith("Offline"));
        state.getJSONArray("agents").getJSONObject(0).put("status", "idle");
        task = TaskSummary.from(state, "noodle", true);
        check(task.status.equals("No task running") && !task.title.equals("Fix tests"));
        check(TaskSummary.from(null, "noodle", true).status.equals("Checking task status"));
        check(TaskSummary.label("waiting", true).equals("Waiting for you"));
        check(TaskSummary.label("compacting", true).equals("Working"));
        check(TaskSummary.label("limited", true).equals("Needs attention"));
    }
    private static void check(boolean ok) { if (!ok) throw new AssertionError("Incorrect task snapshot"); }
}
