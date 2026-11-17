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
    private ProviderHome home;
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
        Usage.screen(this, settings ? "settings" : history ? "history" : "home");
        if (!settings && !history) main.postDelayed(cycleMigo, 8000); startPolling();
    }
    @Override protected void onPause() {
        active = false; generation++; main.removeCallbacks(cycleMigo);
        if (pet != null) pet.stop();
        if (speech != null) speech.stop();
        Usage.pause(this);
        super.onPause();
    }
    @Override protected void onDestroy() {
        polls.shutdownNow(); commands.shutdownNow();
        if (speech != null) speech.shutdown();
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void page(String title) {
        main.removeCallbacks(cycleMigo); companion = null; companionCaption = null; taskCard = null; home = null;
        if (pet != null) { pet.stop(); pet = null; }
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        // Round screens need space above and below the first and last controls.
        content.setPadding(dp(22), dp(22), dp(22), dp(40));
        scroll.setBackgroundColor(Skin.NOW.bg);
        scroll.addView(content);
        setContentView(scroll);
        heading = label(title, 18);
        heading.setTypeface(Skin.NOW.displayFace());
        scroll.setOnGenericMotionListener((v, event) -> {
            if (event.getAction() != android.view.MotionEvent.ACTION_SCROLL) return false;
            scroll.smoothScrollBy(0, Math.round(-event.getAxisValue(android.view.MotionEvent.AXIS_SCROLL) * dp(36)));
            return true;
        });
        scroll.requestFocus();
    }
    private TextView label(String text, int size) {
        TextView v = Ui.text(this, text, size, Skin.NOW.ink, Skin.NOW.bodyFace());
        v.setPadding(0, dp(4), 0, dp(4));
        content.addView(v, new LinearLayout.LayoutParams(-1, -2));
        return v;
    }
    private TextView caption(String text) {
        TextView v = label(text, 11);
        v.setTextColor(Skin.NOW.muted); v.setGravity(Gravity.START); v.setPadding(dp(6), dp(10), dp(6), dp(4));
        return v;
    }
    private Button button(String title, Runnable action) { return button(title, Ui.TONAL, action); }
    private Button button(String title, int kind, Runnable action) {
        Button b = Ui.pill(this, title, kind, action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(6);
        content.addView(b, params);
        return b;
    }
    private EditText input(String hint, String value, int type) {
        EditText e = new EditText(this); e.setHint(hint); e.setText(value);
        e.setInputType(type); e.setSingleLine(true); Ui.field(e);
        content.addView(e, new LinearLayout.LayoutParams(-1, -2)); return e;
    }
    private static int statusColor(String status) {
        if (status.startsWith("Needs") || status.startsWith("Waiting")) return Skin.WARN;
        if (status.startsWith("Offline") || status.startsWith("Agent offline") || status.startsWith("Checking")) return Skin.NOW.muted;
        return Skin.NOW.label;
    }
    private void notice(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }

    // shortcut: manual pairing for sideloaded builds, replace with phone pairing before store release.
    @android.annotation.SuppressLint("WearPasswordInput")
    private void showSettings() {
        Usage.screen(this, "settings");
        settings = true; generation++;
        page("Connect " + BuildConfig.APP_TITLE);
        caption("Hub HTTPS address");
        EditText url = input("https://your-hub", prefs.getString("url", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        caption("Watch token");
        EditText token = input("Watch token", prefs.getString("token", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Ui.spacer(this, content, 6);
        button("Save connection", Ui.PRIMARY, () -> {
            try {
                HubConnection next = new HubConnection(url.getText().toString(), token.getText().toString(), BuildConfig.DEBUG);
                prefs.edit().putString("url", next.base).putString("token", token.getText().toString().trim()).apply();
                hub = next; state = null; connected = false; settings = false;
                showHome(); startPolling();
            } catch (IllegalArgumentException e) { notice(e.getMessage()); }
        });
        if (hub != null) button("Cancel", Ui.OUTLINE, () -> { settings = false; showHome(); startPolling(); });
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
                    String follow = AgentVoice.onHub(next, selected);
                    if (!follow.equals(selected)) { selected = follow; migo = follow; prefs.edit().putString("agent", follow).apply(); changed = true; }
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
        Usage.screen(this, "home");
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
        heading.setTextSize(16); heading.setPadding(0, 0, 0, 0);
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
        companionCaption.setTextColor(0xff80e1d6);
        scroll.post(() -> scroll.scrollTo(0, position));
    }

    private void showProviderHome() {
        Usage.screen(this, "home");
        if (getResources().getConfiguration().fontScale >= 1.3f) {
            page(BuildConfig.APP_TITLE);
            connectionMark = label("", 14);
            taskCard = button("Current task", this::showConversation);
            button("Talk to " + AgentVoice.name(selected), Ui.PRIMARY, this::startLive);
            button("Conversation history", this::showConversation);
            button("Connection settings", this::showSettings);
            updateHeading();
            return;
        }
        main.removeCallbacks(cycleMigo);
        if (pet != null) { pet.stop(); pet = null; }
        companion = null; scroll = null;
        home = new ProviderHome(this, BuildConfig.FIXED_AGENT,
            this::startLive, this::showConversation, this::showSettings);
        connectionMark = home.status;
        taskCard = home.task;
        setContentView(home); updateHeading();
    }

    private void showConversation() {
        Usage.screen(this, "history");
        int position = history ? scroll.getScrollY() : 0;
        history = true;
        String name = AgentVoice.name(selected);
        for (int i = 0; i < array("agents").length(); i++) {
            JSONObject agent = array("agents").optJSONObject(i);
            if (agent != null && selected.equals(agent.optString("id"))) name = agent.optString("name", name);
        }
        page(name);
        Skin skin = Skin.NOW;
        if (!BuildConfig.FIXED_AGENT.isEmpty()) {
            TaskSummary task = TaskSummary.from(state, selected, connected);
            TextView status = label(task.status, 11);
            status.setTextColor(statusColor(task.status)); status.setTypeface(skin.strongFace()); status.setLetterSpacing(.06f);
            label(task.title, 14).setTypeface("serif".equals(skin.display) ? skin.displayFace() : skin.bodyFace());
            if (!task.detail.isBlank() && !task.detail.equals(task.title)) label(task.detail, 12).setTextColor(skin.muted);
            Ui.spacer(this, content, 4);
        }
        button("Live voice", Ui.PRIMARY, this::startLive);
        button("Type a message", this::composeMessage).setEnabled(!sending);
        button("Recent chats", () -> startActivity(new Intent(this, WorkspaceActivity.class).putExtra("agent", selected).putExtra("chats", true)));
        button("Agent workspace", () -> startActivity(new Intent(this, WorkspaceActivity.class).putExtra("agent", selected)));
        if (state == null) label(connected ? "No messages yet" : "Waiting for the hub. Check your connection.", 12).setTextColor(skin.muted);
        for (int i = 0; i < array("agents").length(); i++) {
            JSONObject agent = array("agents").optJSONObject(i);
            if (agent != null && selected.equals(agent.optString("id")) && ("blocked".equals(agent.optString("status")) || "limited".equals(agent.optString("status")))) {
                label(agent.optString("line", "Agent needs attention"), 12).setTextColor(Skin.WARN);
            }
        }
        JSONArray approvals = array("approvals");
        for (int i = 0; i < approvals.length(); i++) {
            JSONObject a = approvals.optJSONObject(i);
            if (a == null || !selected.equals(a.optString("session"))) continue;
            LinearLayout request = new LinearLayout(this); request.setOrientation(LinearLayout.VERTICAL);
            request.setPadding(dp(14), dp(10), dp(14), dp(10));
            request.setBackground(Ui.panel(this, skin.surface, Skin.WARN, skin.card));
            String[] parts = {"Needs approval · " + a.optString("tool"), a.optString("description"), a.optString("preview")};
            for (int part = 0; part < parts.length; part++) {
                if (parts[part].isBlank()) continue;
                TextView line = Ui.text(this, parts[part], part == 1 ? 13 : 11, part == 0 ? Skin.WARN : part == 1 ? skin.ink : skin.muted,
                    part == 0 ? skin.strongFace() : part == 2 ? android.graphics.Typeface.MONOSPACE : skin.bodyFace());
                line.setGravity(Gravity.START); line.setPadding(0, dp(2), 0, dp(2)); request.addView(line);
            }
            LinearLayout.LayoutParams around = new LinearLayout.LayoutParams(-1, -2); around.topMargin = dp(12);
            content.addView(request, around);
            String id = a.optString("requestId");
            Button allow = button("Hold to allow", Ui.WARN, () -> notice("Hold to approve this request"));
            allow.setEnabled(!sending);
            allow.setOnLongClickListener(v -> { verdict(id, true); return true; });
            button("Deny", Ui.OUTLINE, () -> verdict(id, false)).setEnabled(!sending);
        }
        Ui.spacer(this, content, 8);
        String latest = "";
        JSONArray lines = array("spoken");
        for (int i = lines.length() - 1; i >= 0; i--) {
            JSONObject line = lines.optJSONObject(i);
            if (line == null || !selected.equals(line.optString("agent", "noodle"))) continue;
            String text = line.optString("text");
            boolean mine = "echo".equals(line.optString("kind"));
            if (!text.isBlank()) Ui.bubble(this, content, text, mine);
            if (latest.isEmpty() && !mine) latest = text;
            if (line.optJSONObject("card") != null) {
                WatchCard card = new WatchCard(this);
                LinearLayout.LayoutParams around = new LinearLayout.LayoutParams(-1, -2); around.topMargin = dp(6);
                content.addView(card, around); card.show(line.optJSONObject("card"));
            }
            JSONObject artifact = line.optJSONObject("artifact");
            if (artifact != null && artifact.optString("id").matches("[0-9a-f]{12}")) {
                button("Open " + artifact.optString("title", "attachment"), () -> startActivity(
                    new Intent(this, WorkspaceActivity.class).putExtra("agent", selected).putExtra("artifact", artifact.toString())));
            }
        }
        Ui.spacer(this, content, 8);
        String reply = latest;
        if (!reply.isEmpty()) button("Read reply aloud", () -> {
            if (speechReady) speech.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "reply");
            else notice("Speech output is unavailable");
        });
        button("Back to home", Ui.OUTLINE, this::showHome);
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
            Usage.event(this, "message_sent");
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
            TaskSummary task = TaskSummary.from(state, selected, connected);
            if (taskCard != null) {
                // The line above the card already says the hub is offline.
                taskCard.setText(Ui.labelled(home != null && !connected ? "Last known task" : task.status, statusColor(task.status),
                    (home != null && "noodle".equals(BuildConfig.FIXED_AGENT) ? "> " : "") + task.title, true));
                taskCard.setContentDescription("Open current task. " + task.status + ". " + task.title);
            }
            if (home != null) home.state(!connected ? ProviderHome.OFFLINE : statusColor(task.status) == Skin.WARN ? ProviderHome.ATTENTION
                : "Working".equals(task.status) ? ProviderHome.WORKING : ProviderHome.IDLE);
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
            mark.setSpan(new android.text.style.ForegroundColorSpan(!connected ? Skin.NOW.muted : status.startsWith("Needs") ? Skin.WARN : Skin.NOW.label),
                0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            connectionMark.setText(mark);
            connectionMark.setTextColor(Skin.NOW.muted);
            connectionMark.setContentDescription(status + ("Needs approval".equals(status) ? ". Open conversation history to review." : ""));
            return;
        }
        connectionMark.setText(connected ? "✓" : "·");
        connectionMark.setTextColor(connected ? 0xff80e1d6 : 0xffffc58f);
        connectionMark.setContentDescription(connected ? "Connected" : "Reconnecting");
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
