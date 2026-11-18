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
            if (artifact != null) showArtifact(new JSONObject(artifact));
            else if (getIntent().getStringExtra("chat") != null) showChat(getIntent().getStringExtra("chat"), getIntent().getStringExtra("title"));
            else if (getIntent().getBooleanExtra("chats", false)) showChats(); else showWorkspace();
        } catch (Exception e) { page("Unavailable"); note("Connect the app to your hub first."); button("Back", Ui.OUTLINE, this::finish); }
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void page(String title) {
        generation++;
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(22), dp(22), dp(40)); scroll.setBackgroundColor(Skin.NOW.bg);
        scroll.addView(content); setContentView(scroll);
        TextView heading = text(title); heading.setTextSize(18); heading.setTypeface(Skin.NOW.displayFace());
        scroll.setOnGenericMotionListener((v, event) -> {
            if (event.getAction() != android.view.MotionEvent.ACTION_SCROLL) return false;
            scroll.smoothScrollBy(0, Math.round(-event.getAxisValue(android.view.MotionEvent.AXIS_SCROLL) * dp(36))); return true;
        }); scroll.requestFocus();
    }
    private TextView text(String words) {
        TextView view = Ui.text(this, words, 13, Skin.NOW.ink, Skin.NOW.bodyFace());
        view.setLineSpacing(dp(2), 1f); view.setPadding(0, dp(5), 0, dp(5)); content.addView(view); return view;
    }
    private TextView note(String words) { TextView view = text(words); view.setTextSize(12); view.setTextColor(Skin.NOW.muted); return view; }
    /** A labelled block of detail on its own surface. */
    private void panel(String label, String words) {
        TextView view = Ui.text(this, Ui.labelled(label, Skin.NOW.label, words, false), 13, Skin.NOW.ink, Skin.NOW.bodyFace());
        view.setGravity(Gravity.START); view.setLineSpacing(dp(2), 1f); view.setPadding(dp(14), dp(10), dp(14), dp(10));
        view.setBackground(Ui.panel(this, Skin.NOW.surface, Skin.NOW.outlined ? Skin.NOW.line : 0, Skin.NOW.card));
        LinearLayout.LayoutParams around = new LinearLayout.LayoutParams(-1, -2); around.topMargin = dp(8);
        content.addView(view, around);
    }
    private Button button(String words, Runnable action) { return button(words, Ui.TONAL, action); }
    private Button button(String words, int kind, Runnable action) {
        Button view = Ui.pill(this, words, kind, action);
        LinearLayout.LayoutParams around = new LinearLayout.LayoutParams(-1, -2); around.topMargin = dp(6);
        content.addView(view, around); return view;
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
        Usage.screen(this, "workspace");
        page(AgentVoice.name(agent));
        note("Current agent session");
        button("Models", this::showModels);
        button("Background alerts", this::showAlerts);
        button("Refresh", Ui.OUTLINE, this::showWorkspace);
        button("Back", Ui.OUTLINE, this::finish);
        load("/v1/state?device=watch", state -> {
            JSONArray agents = state.optJSONArray("agents");
            if (agents != null) for (int i = 0; i < agents.length(); i++) {
                JSONObject item = agents.getJSONObject(i);
                if (agent.equals(item.optString("id"))) panel("Session", ("Runtime: " + item.optString("runtime") + "\nModel: " + item.optString("model", "Default") + "\n" + item.optString("status") + "\n" + item.optString("line", "")).trim());
            }
            JSONArray tools = state.optJSONArray("tools"); int shown = 0;
            if (tools != null) for (int i = tools.length() - 1; i >= 0; i--) {
                JSONObject tool = tools.getJSONObject(i);
                if (!agent.equals(tool.optString("session"))) continue;
                panel(tool.optString("tool", tool.optString("type")), tool.optString("text", tool.optString("description", ""))); shown++;
            }
            if (shown == 0) note("No recent tool activity.");
        });
    }
    /** Chats this provider has had on the hub's computer; the watch only reads them. */
    private void showChats() {
        Usage.screen(this, "chats");
        page("Recent chats"); note("On your computer"); int request = generation;
        worker.execute(() -> {
            JSONArray chats = null;
            try { chats = new JSONObject(hub.request("/v1/chats?agent=" + agent, null)).optJSONArray("chats"); } catch (Exception ignored) { }
            final JSONArray found = chats;
            runOnUiThread(() -> {
                if (isDestroyed() || generation != request) return;
                if (found == null) note("This hub cannot show chats from your computer. Connect the app to the hub running there.");
                else if (found.length() == 0) note("No " + AgentVoice.name(agent) + " chats found on your computer.");
                else for (int i = 0; i < found.length(); i++) {
                    JSONObject chat = found.optJSONObject(i);
                    if (chat == null) continue;
                    String title = chat.optString("title"), meta = (ago(chat.optLong("at")) + " · " + chat.optString("project")).replaceAll(" · $", "");
                    String brief = title.replaceAll("https?://(www\\.)?", "");   // a link wraps badly on a round screen
                    String shown = brief.length() > 40 ? brief.substring(0, 39).trim() + "…" : brief;   // leaves the last line for where and when
                    android.text.SpannableString words = new android.text.SpannableString(shown + "\n" + meta);
                    words.setSpan(new android.text.style.ForegroundColorSpan(Skin.NOW.muted), shown.length(), words.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    words.setSpan(new android.text.style.RelativeSizeSpan(.8f), shown.length(), words.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    Button row = button("", () -> showChat(chat.optString("id"), title));
                    row.setText(words); row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); row.setMaxLines(3);
                    row.setEllipsize(android.text.TextUtils.TruncateAt.END); row.setTextSize(13);
                    row.setContentDescription(title + ". " + meta);
                }
                button("Back", Ui.OUTLINE, this::finish);
            });
        });
    }
    private void showChat(String id, String title) {
        Usage.screen(this, "chat");
        page(AgentVoice.name(agent)); note(title).setMaxLines(3); int request = generation;
        worker.execute(() -> {
            JSONArray messages = null;
            try { messages = new JSONObject(hub.request("/v1/chats/" + id + "?agent=" + agent, null)).optJSONArray("messages"); } catch (Exception ignored) { }
            final JSONArray found = messages;
            runOnUiThread(() -> {
                if (isDestroyed() || generation != request) return;
                if (found == null) note("Could not load this chat. Try again.");
                else if (found.length() == 0) note("No messages to show.");
                else for (int i = 0; i < found.length(); i++) {
                    JSONObject message = found.optJSONObject(i);
                    if (message != null) Ui.bubble(this, content, message.optString("text"), "user".equals(message.optString("role")));
                }
                Ui.spacer(this, content, 8);
                button("Back", Ui.OUTLINE, this::showChats);
            });
        });
    }
    static String ago(long at) {
        long minutes = Math.max(0, (System.currentTimeMillis() - at) / 60000);
        return minutes < 1 ? "now" : minutes < 60 ? minutes + "m ago" : minutes < 1440 ? minutes / 60 + "h ago" : minutes / 1440 + "d ago";
    }
    private void showAlerts() {
        Usage.screen(this, "alerts");
        page("Background alerts");
        var prefs = getSharedPreferences("hub", MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("alerts", false);
        text(enabled ? "Background checks enabled" : "Background checks disabled").setTextColor(enabled ? Skin.NOW.label : Skin.NOW.muted);
        note("Checks about every 15 minutes when Android permits. Alerts can be delayed while asleep or offline. No message content appears on the lock screen.");
        button(enabled ? "Disable alerts" : "Enable alerts", enabled ? Ui.TONAL : Ui.PRIMARY, () -> {
            if (!enabled && android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 41); return;
            }
            toggleAlerts(!enabled);
        });
        button("Back", Ui.OUTLINE, this::showWorkspace);
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
        Usage.screen(this, "models");
        page("Models"); button("Back", Ui.OUTLINE, this::showWorkspace);
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
        Usage.screen(this, "attachment");
        page(artifact.optString("title", "Attachment")); button("Back", Ui.OUTLINE, this::finish);
        String id = artifact.optString("id");
        if (!validArtifactId(id)) { text("Invalid attachment"); return; }
        TextView status = note("Loading preview…"); int request = generation;
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
    @Override protected void onPause() { Usage.pause(this); super.onPause(); }
    @Override public void onDestroy() { generation++; worker.shutdownNow(); super.onDestroy(); }
}
