package club.chums.wear;

import org.json.JSONArray;
import org.json.JSONObject;

/** A snapshot of this provider's task; disconnected snapshots are never called live. */
final class TaskSummary {
    final String status, title, detail;
    TaskSummary(String status, String title, String detail) {
        this.status = status; this.title = title; this.detail = detail;
    }
    static TaskSummary from(JSONObject state, String agent, boolean connected) {
        String status = "unknown", detail = "", title = "";
        JSONArray agents = state == null ? null : state.optJSONArray("agents");
        if (agents != null) for (int i = 0; i < agents.length(); i++) {
            JSONObject item = agents.optJSONObject(i);
            if (item != null && agent.equals(item.optString("id"))) {
                status = item.optString("status", "unknown");
                detail = item.isNull("line") ? "" : item.optString("line");
            }
        }
        JSONArray spoken = state == null ? null : state.optJSONArray("spoken");
        if (spoken != null) for (int i = spoken.length() - 1; i >= 0; i--) {
            JSONObject item = spoken.optJSONObject(i);
            if (item != null && mine(item, agent) && "echo".equals(item.optString("kind"))) {
                title = item.optString("text"); break;
            }
        }
        JSONArray approvals = state == null ? null : state.optJSONArray("approvals");
        if (approvals != null) for (int i = 0; i < approvals.length(); i++) {
            JSONObject item = approvals.optJSONObject(i);
            if (item != null && agent.equals(item.optString("session"))) {
                status = "approval"; detail = item.optString("description", item.optString("tool")); break;
            }
        }
        String label = label(status, connected);
        boolean idle = "idle".equals(status);
        if (idle) title = "Open recent conversation";
        else if (title.isBlank()) title = detail.isBlank() ? "Open task details" : detail;
        return new TaskSummary(label, title.replaceAll("\\s+", " ").trim(), detail);
    }
    /** A line belongs to an agent by its "agent" field, or by its "session" for a hub that sets only that. */
    static boolean mine(JSONObject line, String agent) {
        String owner = line.optString("agent");
        return agent.equals(owner.isEmpty() ? line.optString("session") : owner);
    }
    /** The part of a state to keep on the watch for one agent: no voices and no other agent's lines. */
    static JSONObject keep(JSONObject state, String agent) {
        JSONObject kept = new JSONObject();
        try {
            kept.put("rev", state.optLong("rev"));
            for (String key : new String[]{"agents", "sessions", "approvals", "tools", "spoken"}) {
                JSONArray from = state.optJSONArray(key), to = new JSONArray();
                if (from != null) for (int i = 0; i < from.length(); i++) {
                    JSONObject item = from.optJSONObject(i);
                    if (item == null) continue;
                    if ("spoken".equals(key)) {
                        if (!mine(item, agent)) continue;
                        item = new JSONObject(item.toString()); item.remove("audioData"); item.remove("audio");
                    }
                    to.put(item);
                }
                kept.put(key, to);
            }
        } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
        return kept;
    }
    static String label(String status, boolean connected) {
        if (!connected) return "Offline · last known task";
        return switch (status) {
            case "working", "busy", "compacting" -> "Working";
            case "approval" -> "Needs approval";
            case "waiting" -> "Waiting for you";
            case "blocked", "limited" -> "Needs attention";
            case "idle" -> "No task running";
            case "offline" -> "Agent offline";
            default -> "Checking task status";
        };
    }
}
