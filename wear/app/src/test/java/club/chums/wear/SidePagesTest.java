package club.chums.wear;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class SidePagesTest {
    @Test public void agesAreShort() {
        long now = 10L * 24 * 60 * 60_000;
        assertEquals("", WatchUi.ago(0, now));
        assertEquals("now", WatchUi.ago(now - 20_000, now));
        assertEquals("5m", WatchUi.ago(now - 5 * 60_000, now));
        assertEquals("2h", WatchUi.ago(now - 130 * 60_000, now));
        assertEquals("3d", WatchUi.ago(now - 3L * 24 * 60 * 60_000, now));
        assertEquals("now", WatchUi.ago(now + 5_000, now));
    }
    @Test public void aLineBelongsToItsAgentOrElseItsSession() throws Exception {
        assertTrue(TaskSummary.mine(new JSONObject().put("agent", "squint").put("session", "Chums"), "squint"));
        assertTrue(TaskSummary.mine(new JSONObject().put("session", "squint"), "squint"));
        assertFalse(TaskSummary.mine(new JSONObject().put("session", "Chums"), "squint"));
        assertFalse(TaskSummary.mine(new JSONObject().put("agent", "noodle").put("session", "squint"), "squint"));
    }
    @Test public void theKeptStateHasNoVoicesAndNoOtherAgentsLines() throws Exception {
        JSONObject state = new JSONObject().put("rev", 7)
            .put("agents", new JSONArray().put(new JSONObject().put("id", "squint").put("status", "working")))
            .put("spoken", new JSONArray()
                .put(new JSONObject().put("agent", "squint").put("text", "Fix the build").put("kind", "echo").put("audioData", "AAAA"))
                .put(new JSONObject().put("agent", "noodle").put("text", "Other")));
        JSONObject kept = TaskSummary.keep(state, "squint");
        assertEquals(7, kept.getLong("rev"));
        assertEquals(1, kept.getJSONArray("spoken").length());
        assertFalse(kept.getJSONArray("spoken").getJSONObject(0).has("audioData"));
        assertTrue(state.getJSONArray("spoken").getJSONObject(0).has("audioData"));
        assertEquals("Offline · last known task", TaskSummary.from(kept, "squint", false).status);
        assertEquals("Fix the build", TaskSummary.from(kept, "squint", false).title);
    }
}
