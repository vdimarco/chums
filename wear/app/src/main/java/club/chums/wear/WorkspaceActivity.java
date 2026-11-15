package club.chums.wear;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import org.json.*;
import java.util.concurrent.*;

/** Read the configured hub's capabilities; never substitute another provider. */
public final class WorkspaceActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private HubConnection hub;
    private String agent;
    private int generation;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        var prefs = getSharedPreferences("hub", MODE_PRIVATE);
        agent = AgentVoice.forApp(getIntent().getStringExtra("agent"));
        try {
            AgentVoice.name(agent);
            hub = new HubConnection(prefs.getString("url", ""), prefs.getString("token", ""), BuildConfig.DEBUG);
            String artifact = getIntent().getStringExtra("artifact");
            if (artifact != null) showArtifact(new JSONObject(artifact)); else showWorkspace();
        } catch (Exception e) { page("Unavailable"); text("Connect the app to your hub first."); button("Back", this::finish); }
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void page(String title) {
        generation++;
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(26), dp(28), dp(26), dp(36)); content.setBackgroundColor(ProviderHome.background());
        scroll.addView(content); setContentView(scroll); text(title).setTextSize(19);
        scroll.setOnGenericMotionListener((v, event) -> {
            if (event.getAction() != android.view.MotionEvent.ACTION_SCROLL) return false;
            scroll.smoothScrollBy(0, Math.round(-event.getAxisValue(android.view.MotionEvent.AXIS_SCROLL) * dp(36))); return true;
        }); scroll.requestFocus();
    }
    private TextView text(String words) {
        TextView view = new TextView(this); view.setText(words); view.setTextSize(14); view.setTextColor(ProviderHome.ink());
        view.setGravity(Gravity.CENTER); view.setPadding(0, dp(6), 0, dp(6)); content.addView(view); return view;
    }
    private Button button(String words, Runnable action) {
        Button view = new Button(this); view.setText(words); view.setAllCaps(false); view.setMinHeight(dp(48));
        content.addView(view, new LinearLayout.LayoutParams(-1, -2)); view.setOnClickListener(v -> action.run()); return view;
    }
    private interface Result { void show(JSONObject value) throws Exception; }
    private void load(String path, Result result) {
        int request = generation;
        worker.execute(() -> {
            try {
                JSONObject value = new JSONObject(hub.request(path, null));
                runOnUiThread(() -> {
                    if (isDestroyed() || generation != request) return;
                    try { result.show(value); } catch (Exception e) { text("The hub returned an unsupported response."); }
                });
            } catch (Exception e) { runOnUiThread(() -> {
                if (!isDestroyed() && generation == request) text("Could not load this feature. " + e.getMessage());
            }); }
        });
    }
    private void showWorkspace() {
        page(AgentVoice.name(agent));
        text("Current agent session");
        button("Models", this::showModels);
        button("Background alerts", this::showAlerts);
        button("Refresh", this::showWorkspace);
        button("Back", this::finish);
        load("/v1/state?device=watch", state -> {
            JSONArray agents = state.optJSONArray("agents");
            if (agents != null) for (int i = 0; i < agents.length(); i++) {
                JSONObject item = agents.getJSONObject(i);
                if (agent.equals(item.optString("id"))) text("Runtime: " + item.optString("runtime") + "\nModel: " + item.optString("model", "Default") + "\n" + item.optString("status") + "\n" + item.optString("line", ""));
            }
            JSONArray tools = state.optJSONArray("tools"); int shown = 0;
            if (tools != null) for (int i = tools.length() - 1; i >= 0; i--) {
                JSONObject tool = tools.getJSONObject(i);
                if (!agent.equals(tool.optString("session"))) continue;
                text(tool.optString("tool", tool.optString("type")) + "\n" + tool.optString("text", tool.optString("description", ""))); shown++;
            }
            if (shown == 0) text("No recent tool activity.");
        });
    }
    private void showAlerts() {
        page("Background alerts");
        var prefs = getSharedPreferences("hub", MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("alerts", false);
        text(enabled ? "Background checks enabled" : "Background checks disabled");
        text("Checks about every 15 minutes when Android permits. Alerts can be delayed while asleep or offline. No message content appears on the lock screen.");
        button(enabled ? "Disable alerts" : "Enable alerts", () -> {
            if (!enabled && android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 41); return;
            }
            toggleAlerts(!enabled);
        });
        button("Back", this::showWorkspace);
    }
    private void toggleAlerts(boolean enabled) {
        if (enabled) {
            load("/v1/state?device=watch", state -> {
                getSharedPreferences("hub", MODE_PRIVATE).edit().putStringSet("alerts-seen:" + hub.base + ":" + agent,
                    new java.util.HashSet<>(AgentAlerts.events(state, agent).keySet())).apply();
                applyAlerts(true);
            });
        } else applyAlerts(false);
    }
    private void applyAlerts(boolean enabled) {
        if (!AgentAlerts.schedule(this, enabled)) { text("Android could not schedule checks. Try again later."); return; }
        getSharedPreferences("hub", MODE_PRIVATE).edit().putBoolean("alerts", enabled).apply();
        showAlerts();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 41 && results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) toggleAlerts(true);
    }
    private void showModels() {
        page("Models"); button("Back", this::showWorkspace);
        load("/v1/state?device=watch", state -> {
            JSONArray agents = state.optJSONArray("agents"); String runtime = "";
            if (agents != null) for (int i = 0; i < agents.length(); i++) {
                JSONObject item = agents.getJSONObject(i);
                if (agent.equals(item.optString("id"))) runtime = item.optString("runtime");
            }
            final String selectedRuntime = runtime;
            load("/v1/models", catalog -> {
                JSONObject provider = catalog.optJSONObject(selectedRuntime);
                JSONArray models = provider == null ? null : provider.optJSONArray("models");
                if (models == null || models.length() == 0) { text("This hub does not offer model selection for this agent."); return; }
                for (int i = 0; i < models.length(); i++) {
                    JSONObject model = models.getJSONObject(i); String id = model.getString("id");
                    button(model.optString("name", id), () -> new android.app.AlertDialog.Builder(this)
                        .setTitle("Use " + model.optString("name", id) + "?")
                        .setMessage("Changes the model for this agent on the hub.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Use model", (d, which) -> setModel(id)).show());
                }
            });
        });
    }
    private void setModel(String model) {
        page("Updating model…"); int request = generation;
        worker.execute(() -> {
            String result;
            try {
                JSONObject response = new JSONObject(hub.request("/v1/agents/" + agent + "/model", new JSONObject().put("model", model).toString()));
                result = response.optString("error", "Model updated");
            } catch (Exception e) { result = "Update not confirmed. Refresh before trying again."; }
            final String message = result;
            runOnUiThread(() -> { if (!isDestroyed() && request == generation) { text(message); button("Refresh workspace", this::showWorkspace); } });
        });
    }
    private void showArtifact(JSONObject artifact) {
        page(artifact.optString("title", "Attachment")); button("Back", this::finish);
        String id = artifact.optString("id");
        if (!validArtifactId(id)) { text("Invalid attachment"); return; }
        TextView status = text("Loading preview…"); int request = generation;
        worker.execute(() -> {
            try {
                byte[] bytes = hub.requestBytes("/v1/artifact/" + id + "/full", null);
                android.graphics.Bitmap bitmap = WatchCard.decode(new java.io.ByteArrayInputStream(bytes));
                runOnUiThread(() -> {
                    if (isDestroyed() || request != generation) { bitmap.recycle(); return; }
                    status.setText("Preview — scroll to read");
                    ImageView image = new ImageView(this); image.setImageBitmap(bitmap); image.setAdjustViewBounds(true);
                    image.setContentDescription(artifact.optString("title", "Attachment preview")); content.addView(image, new LinearLayout.LayoutParams(-1, -2));
                });
            } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed() && request == generation) status.setText("Preview unavailable on this hub."); }); }
        });
    }
    static boolean validArtifactId(String id) { return id != null && id.matches("[0-9a-f]{12}"); }
    @Override public void onDestroy() { generation++; worker.shutdownNow(); super.onDestroy(); }
}
