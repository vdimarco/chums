package club.chums.wear;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService polls = Executors.newSingleThreadExecutor();
    private final ExecutorService commands = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private HubConnection hub;
    private JSONObject state;
    private LinearLayout content;
    private ScrollView scroll;
    private TextView heading, connectionMark, companionCaption, taskCard;
    private String selected = "";
    private boolean connected;
    private int generation;
    private boolean active, settings, sending, speechReady, history;
    private TextToSpeech speech;
    private PetDrawable pet;
    private ImageView companion;
    private String migo = "noodle";
    private static final String[] MIGOS = {"noodle", "squint", "ray", "stretch"};
    private final Runnable cycleMigo = new Runnable() {
        @Override public void run() {
            if (!BuildConfig.FIXED_AGENT.isEmpty() || !active || settings || history || companion == null) return;
            stepMigo(1, false);
            main.postDelayed(this, 8000);
        }
    };
    static int swipeStep(float dx, float dy, float threshold) {
        if (Math.abs(dy) < threshold || Math.abs(dy) <= Math.abs(dx)) return 0;
        return dy < 0 ? 1 : -1;
    }
    private void stepMigo(int step, boolean manual) {
        if (!BuildConfig.FIXED_AGENT.isEmpty()) return;
        int index = java.util.Arrays.asList(MIGOS).indexOf(migo);
        migo = MIGOS[Math.floorMod(index + step, MIGOS.length)];
        showMigo();
        if (manual) {
            selected = migo;
            prefs.edit().putString("agent", selected).apply();
            main.removeCallbacks(cycleMigo);
            main.postDelayed(cycleMigo, 8000);
            companion.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        }
    }
    private void showMigo() {
        if (pet != null) pet.stop();
        pet = new PetDrawable(this, migo, "idle");
        companion.setImageDrawable(pet);
        companion.setContentDescription("Talk live with " + AgentVoice.name(migo));
        if (companionCaption != null) companionCaption.setText(AgentVoice.name(migo) + " · tap to talk");
        if (active) pet.start();
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("hub", MODE_PRIVATE);
        selected = AgentVoice.forApp(prefs.getString("agent", ""));
        if (java.util.Arrays.asList(MIGOS).contains(selected)) migo = selected;
        else selected = migo;
        speech = new TextToSpeech(this, status -> speechReady = status == TextToSpeech.SUCCESS);
        try { hub = new HubConnection(prefs.getString("url", ""), prefs.getString("token", ""), BuildConfig.DEBUG); }
        catch (IllegalArgumentException ignored) { }
        if (hub == null) showSettings(); else showHome();
        main.post(this::handleTileAction);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); main.post(this::handleTileAction);
    }
    private void handleTileAction() {
        String action = getIntent().getStringExtra("tile_action");
        getIntent().removeExtra("tile_action");
        if (action == null) return;
        if (hub == null) { notice("Connect " + BuildConfig.APP_TITLE + " first"); return; }
        if ("history".equals(action)) { showConversation(); return; }
        if ("voice".equals(action) || "agent".equals(action)) startLive();
    }
    private void startLive() {
        selected = migo;
        prefs.edit().putString("agent", selected).apply();
        startActivity(new Intent(this, LiveVoiceActivity.class).putExtra("start_call", true).putExtra("migo", migo));
    }
    @Override protected void onResume() {
        super.onResume(); active = true; if (pet != null) pet.start(); main.removeCallbacks(cycleMigo);
        if (!settings && !history) main.postDelayed(cycleMigo, 8000); startPolling();
    }
    @Override protected void onPause() {
        active = false; generation++; main.removeCallbacks(cycleMigo);
        if (pet != null) pet.stop();
        if (speech != null) speech.stop();
        super.onPause();
    }
    @Override protected void onDestroy() {
        polls.shutdownNow(); commands.shutdownNow();
        if (speech != null) speech.shutdown();
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void page(String title) {
        main.removeCallbacks(cycleMigo); companion = null; companionCaption = null; taskCard = null;
        if (pet != null) { pet.stop(); pet = null; }
        content = WatchUi.page(this);
        scroll = (ScrollView) content.getParent();
        heading = WatchUi.title(content, title);
    }
    private TextView label(String text, int size) { return WatchUi.text(content, text, size, WatchUi.ink()); }
    private Button button(String title, Runnable action) { return WatchUi.button(content, title, false, action); }
    private Button primary(String title, Runnable action) { return WatchUi.button(content, title, true, action); }
    private EditText input(String hint, String value, int type) {
        EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setTextSize(14);
        e.setTextColor(WatchUi.ink()); e.setHintTextColor(WatchUi.muted());
        e.setInputType(type); e.setSingleLine(true); e.setMinHeight(dp(48));
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackground(WatchUi.fill(this, WatchUi.surface(), WatchUi.radius()));
        content.addView(e, new LinearLayout.LayoutParams(-1, -2)); return e;
    }
    private void notice(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }

    // shortcut: manual pairing for sideloaded builds, replace with phone pairing before store release.
    @android.annotation.SuppressLint("WearPasswordInput")
    private void showSettings() {
        settings = true; generation++;
        page("Connect " + BuildConfig.APP_TITLE);
        label("Hub HTTPS address", 12).setTextColor(WatchUi.muted());
        EditText url = input("https://your-hub", prefs.getString("url", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        label("Watch token", 12).setTextColor(WatchUi.muted());
        EditText token = input("Watch token", prefs.getString("token", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        primary("Save connection", () -> {
            try {
                HubConnection next = new HubConnection(url.getText().toString(), token.getText().toString(), BuildConfig.DEBUG);
                prefs.edit().putString("url", next.base).putString("token", token.getText().toString().trim()).apply();
                hub = next; state = null; connected = false; settings = false;
                showHome(); startPolling();
            } catch (IllegalArgumentException e) { notice(e.getMessage()); }
        });
        if (hub != null) button("Cancel", () -> { settings = false; showHome(); startPolling(); });
    }

    private void startPolling() {
        int run = ++generation;
        if (active && !settings && hub != null) poll(run, 0);
    }
    private void poll(int run, long revision) {
        if (!active || settings || run != generation) return;
        HubConnection client = hub;
        polls.execute(() -> {
            try {
                JSONObject next = new JSONObject(client.request("/v1/state?since=" + revision + "&device=watch", null));
                main.post(() -> {
                    if (!active || run != generation) return;
                    boolean changed = state == null || next.optLong("rev") != state.optLong("rev");
                    boolean wasConnected = connected;
                    state = next; connected = true;
                    if ((changed || !wasConnected) && history) showConversation();
                    if (!history) updateHeading();
                    main.postDelayed(() -> poll(run, next.optLong("rev")), 200);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (!active || run != generation) return;
                    boolean wasConnected = connected;
                    connected = false;
                    if (history && wasConnected) showConversation();
                    if (!history) updateHeading();
                    main.postDelayed(() -> poll(run, 0), 5000);
                });
            }
        });
    }

    private JSONArray array(String key) { return state == null ? new JSONArray() : state.optJSONArray(key) == null ? new JSONArray() : state.optJSONArray(key); }
    private void showHome() {
        int position = scroll == null || settings || history ? 0 : scroll.getScrollY();
        settings = false; history = false;
        if (!BuildConfig.FIXED_AGENT.isEmpty()) { showProviderHome(); return; }
        JSONArray agents = array("agents");
        JSONObject agent = null;
        for (int i = 0; i < agents.length(); i++) {
            JSONObject a = agents.optJSONObject(i);
            if (a != null && selected.equals(a.optString("id"))) agent = a;
        }
        if (selected.isEmpty() && agents.length() > 0) { agent = agents.optJSONObject(0); selected = agent.optString("id"); }
        page(BuildConfig.APP_TITLE);
        content.setPadding(dp(18), dp(8), dp(18), dp(8));
        content.removeView(heading);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(top, new LinearLayout.LayoutParams(-1, dp(48)));
        connectionMark = new TextView(this);
        connectionMark.setTextSize(14); connectionMark.setGravity(Gravity.CENTER);
        top.addView(new android.view.View(this), new LinearLayout.LayoutParams(dp(48), -1));
        LinearLayout brand = new LinearLayout(this); brand.setGravity(Gravity.CENTER);
        top.addView(brand, new LinearLayout.LayoutParams(0, -1, 1));
        heading.setTextSize(16); heading.setPadding(0, 0, 0, 0); heading.setTypeface(android.graphics.Typeface.DEFAULT);
        brand.addView(heading, new LinearLayout.LayoutParams(-2, -1));
        brand.addView(connectionMark, new LinearLayout.LayoutParams(dp(20), -1));
        TextView menu = new TextView(this);
        menu.setText("⋯"); menu.setTextSize(22); menu.setTextColor(0xffb5c3c5);
        menu.setGravity(Gravity.CENTER); menu.setContentDescription("Settings and more options");
        menu.setFocusable(true); menu.setOnClickListener(v -> showMenu());
        top.addView(menu, new LinearLayout.LayoutParams(dp(48), -1));
        updateHeading();
        companion = new ImageView(this);
        showMigo();
        companion.setScaleType(ImageView.ScaleType.FIT_CENTER);
        companion.setFocusable(true);
        companion.setOnClickListener(v -> startLive());
        android.view.GestureDetector gestures = new android.view.GestureDetector(this,
            new android.view.GestureDetector.SimpleOnGestureListener() {
                private boolean switched;
                @Override public boolean onDown(android.view.MotionEvent event) { switched = false; return true; }
                @Override public boolean onSingleTapUp(android.view.MotionEvent event) {
                    companion.performClick(); return true;
                }
                @Override public boolean onScroll(android.view.MotionEvent from, android.view.MotionEvent to, float dx, float dy) {
                    if (from == null || switched) return true;
                    int step = swipeStep(to.getX() - from.getX(), to.getY() - from.getY(), dp(24));
                    if (step != 0) { switched = true; stepMigo(step, true); }
                    return true;
                }
            });
        companion.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN)
                scroll.requestDisallowInterceptTouchEvent(true);
            return gestures.onTouchEvent(event);
        });
        FrameLayout character = new FrameLayout(this);
        character.addView(new VoiceGlow(this), new FrameLayout.LayoutParams(-1, -1));
        character.addView(companion, new FrameLayout.LayoutParams(-1, -1));
        content.addView(character, new LinearLayout.LayoutParams(-1, dp(132)));
        if (active) main.postDelayed(cycleMigo, 8000);
        companionCaption = label(AgentVoice.name(migo) + " · tap to talk", 11);
        companionCaption.setTextColor(WatchUi.signal());
        scroll.post(() -> scroll.scrollTo(0, position));
    }

    private void showProviderHome() {
        if (getResources().getConfiguration().fontScale >= 1.3f) {
            page(BuildConfig.APP_TITLE);
            connectionMark = label("", 12);
            taskCard = button("Current task", this::showConversation);
            primary("Talk to " + AgentVoice.name(selected), this::startLive);
            button("Conversation history", this::showConversation);
            button("Connection settings", this::showSettings);
            updateHeading();
            return;
        }
        main.removeCallbacks(cycleMigo);
        if (pet != null) { pet.stop(); pet = null; }
        companion = null; scroll = null;
        ProviderHome home = new ProviderHome(this, BuildConfig.FIXED_AGENT,
            this::startLive, this::showConversation, this::showSettings);
        connectionMark = home.status;
        taskCard = home.task;
        setContentView(home); updateHeading();
    }

    private void showConversation() {
        int position = history ? scroll.getScrollY() : 0;
        history = true;
        String name = AgentVoice.name(selected);
        for (int i = 0; i < array("agents").length(); i++) {
            JSONObject agent = array("agents").optJSONObject(i);
            if (agent != null && selected.equals(agent.optString("id"))) name = agent.optString("name", selected);
        }
        page(name);
        if (!BuildConfig.FIXED_AGENT.isEmpty()) {
            TaskSummary task = TaskSummary.from(state, selected, connected);
            label(task.status, 12).setTextColor(statusColor(task.status));
            label(task.title, 14);
            if (!task.detail.isBlank() && !task.detail.equals(task.title)) label(task.detail, 12).setTextColor(WatchUi.muted());
            WatchUi.space(content, 4);
        }
        primary("Live voice", this::startLive);
        button("Type a message", this::composeMessage).setEnabled(!sending);
        button("Agent workspace", () -> startActivity(new Intent(this, WorkspaceActivity.class).putExtra("agent", selected)));
        if (state == null) label(connected ? "No messages yet" : "Waiting for the hub. Check your connection.", 12).setTextColor(WatchUi.muted());
        for (int i = 0; i < array("agents").length(); i++) {
            JSONObject agent = array("agents").optJSONObject(i);
            if (agent != null && selected.equals(agent.optString("id")) && ("blocked".equals(agent.optString("status")) || "limited".equals(agent.optString("status")))) {
                label(agent.optString("line", "Agent needs attention"), 12).setTextColor(WatchUi.warning());
            }
        }
        JSONArray approvals = array("approvals");
        for (int i = 0; i < approvals.length(); i++) {
            JSONObject a = approvals.optJSONObject(i);
            if (a == null || !selected.equals(a.optString("session"))) continue;
            WatchUi.space(content, 6);
            label("Needs approval", 12).setTextColor(WatchUi.warning());
            WatchUi.card(content, (a.optString("tool") + "\n" + a.optString("description") + "\n" + a.optString("preview")).trim(),
                WatchUi.surface(), Gravity.CENTER);
            String id = a.optString("requestId");
            Button allow = primary("Hold to allow", () -> notice("Hold to approve this request"));
            allow.setEnabled(!sending);
            allow.setOnLongClickListener(v -> { verdict(id, true); return true; });
            button("Deny", () -> verdict(id, false)).setEnabled(!sending);
        }
        String latest = "";
        boolean messages = false;
        JSONArray lines = array("spoken");
        for (int i = lines.length() - 1; i >= 0; i--) {
            JSONObject line = lines.optJSONObject(i);
            if (line == null || !selected.equals(line.optString("agent", "noodle"))) continue;
            String text = line.optString("text");
            boolean own = "echo".equals(line.optString("kind"));
            if (!messages) { messages = true; WatchUi.space(content, 8); label("Messages, newest first", 11).setTextColor(WatchUi.muted()); }
            // The owner's messages are on the right. The agent's messages are on the left.
            TextView message = WatchUi.card(content, text, own ? WatchUi.ownBubble() : WatchUi.surface(), own ? Gravity.END : Gravity.START);
            message.setContentDescription((own ? "You: " : name + ": ") + text);
            if (latest.isEmpty() && !"echo".equals(line.optString("kind"))) latest = text;
            if (line.optJSONObject("card") != null) {
                WatchCard card = new WatchCard(this); content.addView(card); card.show(line.optJSONObject("card"));
            }
            JSONObject artifact = line.optJSONObject("artifact");
            if (artifact != null && artifact.optString("id").matches("[0-9a-f]{12}")) {
                button("Open " + artifact.optString("title", "attachment"), () -> startActivity(
                    new Intent(this, WorkspaceActivity.class).putExtra("agent", selected).putExtra("artifact", artifact.toString())));
            }
        }
        String reply = latest;
        WatchUi.space(content, 6);
        if (!reply.isEmpty()) button("Read reply aloud", () -> {
            if (speechReady) speech.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "reply");
            else notice("Speech output is unavailable");
        });
        button("Back to home", this::showHome);
        scroll.post(() -> scroll.scrollTo(0, position));
    }

    @Override public void onBackPressed() {
        if (history) showHome();
        else if (settings && hub != null) { settings = false; showHome(); startPolling(); }
        else super.onBackPressed();
    }

    private void composeMessage() {
        EditText message = new EditText(this);
        message.setHint("Message " + AgentVoice.name(selected));
        message.setContentDescription("Message to " + AgentVoice.name(selected));
        message.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        message.setMaxLines(4);
        message.setMinHeight(dp(48));
        message.setPadding(dp(26), dp(8), dp(26), dp(8));
        message.setText(prefs.getString("draft", ""));
        message.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4000)});
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Type a message")
            .setView(message).setNegativeButton("Cancel", null).setPositiveButton("Send", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setMinHeight(dp(48));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setMinHeight(dp(48));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String text = message.getText().toString().trim();
            if (text.isEmpty()) { message.setError("Enter a message"); return; }
            if (sending) return;
            prefs.edit().putString("draft", text).apply();
            post("/v1/say", object("agent", selected, "text", text, "device", "watch"));
            dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void showMenu() {
        if (!BuildConfig.FIXED_AGENT.isEmpty()) {
            new AlertDialog.Builder(this).setTitle(BuildConfig.APP_TITLE)
                .setItems(new String[]{"Conversation history", "Connection settings"}, (dialog, index) -> {
                    if (index == 0) showConversation(); else showSettings();
                }).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("migos")
            .setItems(new String[]{"Choose migo", "Conversation history", "Connection settings"}, (dialog, index) -> {
                switch (index) {
                    case 0 -> chooseAgent();
                    case 1 -> showConversation();
                    case 2 -> showSettings();
                }
            }).show();
    }
    private void updateHeading() {
        if (!BuildConfig.FIXED_AGENT.isEmpty()) {
            if (taskCard != null) {
                TaskSummary task = TaskSummary.from(state, selected, connected);
                android.text.SpannableString words = new android.text.SpannableString(task.status + "\n" + task.title);
                words.setSpan(new android.text.style.ForegroundColorSpan(statusColor(task.status)), 0, task.status.length(), 0);
                words.setSpan(new android.text.style.RelativeSizeSpan(.84f), 0, task.status.length(), 0);
                taskCard.setText(words);
                MigosTileService.remember(this, task.status, task.title);
                taskCard.setContentDescription("Open current task. " + task.status + ". " + task.title);
            }
            String status = connected ? "Connected" : "Offline";
            if (connected) {
                for (int i = 0; i < array("agents").length(); i++) {
                    JSONObject agent = array("agents").optJSONObject(i);
                    if (agent != null && selected.equals(agent.optString("id")))
                        status = providerStatus(agent.optString("status"));
                }
                for (int i = 0; i < array("approvals").length(); i++) {
                    JSONObject approval = array("approvals").optJSONObject(i);
                    if (approval != null && selected.equals(approval.optString("session"))) status = "Needs approval";
                }
            }
            android.text.SpannableString mark = new android.text.SpannableString((connected ? "●  " : "○  ") + status);
            mark.setSpan(new android.text.style.ForegroundColorSpan(connected ? statusColor(status) : WatchUi.muted()), 0, 1, 0);
            connectionMark.setText(mark);
            connectionMark.setTextColor(WatchUi.muted());
            connectionMark.setContentDescription(status + ("Needs approval".equals(status) ? ". Open conversation history to review." : ""));
            return;
        }
        connectionMark.setText(connected ? "✓" : "·");
        connectionMark.setTextColor(connected ? WatchUi.signal() : WatchUi.warning());
        connectionMark.setContentDescription(connected ? "Connected" : "Reconnecting");
    }
    /** Amber marks a state that needs the owner. Other states use the provider's signal colour. */
    static int statusColor(String status) {
        if (status.startsWith("Needs") || status.startsWith("Waiting")) return WatchUi.warning();
        if (status.startsWith("Offline") || status.startsWith("Agent offline") || status.startsWith("Checking") || status.startsWith("No task")) return WatchUi.muted();
        return WatchUi.signal();
    }
    static String providerStatus(String status) {
        return switch (status) {
            case "blocked", "limited" -> "Needs attention";
            case "offline" -> "Agent offline";
            case "working", "busy" -> "Working";
            default -> "Connected";
        };
    }
    private void chooseAgent() {
        String[] ids = MIGOS;
        String[] names = {"Codex", "Claude", "Hermes", "Grok"};
        new AlertDialog.Builder(this).setTitle("Choose your migo").setItems(names, (d, index) -> {
            selected = ids[index]; migo = selected;
            prefs.edit().putString("agent", selected).apply();
            androidx.wear.tiles.TileService.getUpdater(this).requestUpdate(MigosTileService.class);
            showHome();
        }).show();
    }
    private void verdict(String id, boolean allow) {
        post("/v1/verdict", object("requestId", id, "behavior", allow ? "allow" : "deny"));
    }
    private static JSONObject object(String... pairs) {
        JSONObject result = new JSONObject();
        try { for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]); }
        catch (JSONException e) { throw new IllegalArgumentException(e); }
        return result;
    }
    private void post(String path, JSONObject body) {
        if (sending || hub == null) return;
        sending = true; if (history) showConversation(); else showHome();
        HubConnection client = hub;
        commands.execute(() -> {
            String message;
            try {
                JSONObject reply = new JSONObject(client.request(path, body.toString()));
                message = reply.optString("error", reply.optString("echo", "Sent"));
                if ("/v1/say".equals(path) && !reply.has("error")) prefs.edit().remove("draft").apply();
            } catch (Exception e) {
                // A timeout may follow a successful delivery; never resend a command automatically.
                message = "Delivery not confirmed. Check the conversation before retrying.";
            }
            String feedback = message;
            main.post(() -> {
                sending = false;
                if (!isDestroyed() && hub == client) {
                    notice(feedback);
                    if (active && !settings) { if (history) showConversation(); else showHome(); }
                }
            });
        });
    }
}
