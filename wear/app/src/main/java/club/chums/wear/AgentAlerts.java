package club.chums.wear;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.util.*;

/** Periodic background checks; Android decides when the watch can run them. */
public final class AgentAlerts extends JobService {
    private java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
    static boolean schedule(Context context, boolean enabled) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (!enabled) { scheduler.cancel(41); return true; }
        return scheduler.schedule(new JobInfo.Builder(41, new ComponentName(context, AgentAlerts.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setRequiresBatteryNotLow(true)
            .setPeriodic(15 * 60 * 1000L).setPersisted(true).build()) == JobScheduler.RESULT_SUCCESS;
    }
    static Map<String, String> events(JSONObject state, String agent) {
        Map<String, String> result = new LinkedHashMap<>();
        JSONArray spoken = state.optJSONArray("spoken");
        if (spoken != null) for (int i = 0; i < spoken.length(); i++) {
            JSONObject line = spoken.optJSONObject(i);
            if (line != null && agent.equals(line.optString("agent")) && isTerminal(line.optString("kind")) && !line.optString("id").isEmpty())
                result.put("reply:" + line.optString("id"), "Agent update available");
        }
        JSONArray approvals = state.optJSONArray("approvals");
        if (approvals != null) for (int i = 0; i < approvals.length(); i++) {
            JSONObject item = approvals.optJSONObject(i);
            if (item != null && agent.equals(item.optString("session")) && !item.optString("requestId").isEmpty())
                result.put("approval:" + item.optString("requestId"), "Approval needed");
        }
        return result;
    }
    static boolean isTerminal(String kind) { return Set.of("done", "blocked", "question", "error", "artifact").contains(kind); }
    @Override public boolean onStartJob(JobParameters params) {
        final var stop = new java.util.concurrent.atomic.AtomicBoolean();
        cancelled = stop;
        new Thread(() -> {
            try {
                var prefs = getSharedPreferences("hub", MODE_PRIVATE);
                if (!prefs.getBoolean("alerts", false)) return;
                String agent = AgentVoice.forApp(prefs.getString("agent", "noodle"));
                HubConnection hub = new HubConnection(prefs.getString("url", ""), prefs.getString("token", ""), BuildConfig.DEBUG);
                Map<String, String> current = events(new JSONObject(hub.request("/v1/state?device=watch", null)), agent);
                if (stop.get()) return;
                String key = "alerts-seen:" + hub.base + ":" + agent;
                Set<String> seen = prefs.getStringSet(key, null);
                String update = null;
                for (var event : current.entrySet()) if (seen != null && !seen.contains(event.getKey())) update = event.getValue();
                NotificationManager notifications = getSystemService(NotificationManager.class);
                if (update != null && !stop.get() && prefs.getBoolean("alerts", false) && notifications.areNotificationsEnabled()) {
                    notifications.createNotificationChannel(new NotificationChannel("agents", "Agent updates", NotificationManager.IMPORTANCE_DEFAULT));
                    Intent intent = new Intent(this, MainActivity.class).putExtra("tile_action", "history");
                    PendingIntent open = PendingIntent.getActivity(this, 41, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    notifications.notify(41, new Notification.Builder(this, "agents").setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(AgentVoice.name(agent)).setContentText(update).setContentIntent(open)
                        .setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true).build());
                }
                if (!stop.get()) prefs.edit().putStringSet(key, new HashSet<>(current.keySet())).apply();
            } catch (Exception ignored) {
                // A failed check keeps its cursor; the next scheduled check can recover.
            } finally { new Handler(Looper.getMainLooper()).post(() -> { if (!stop.get()) jobFinished(params, false); }); }
        }, "agent-alerts").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) { cancelled.set(true); return true; }
}
