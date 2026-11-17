package club.chums.wear;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/**
 * What gets used on the watch: which screens are opened, how long they stay up and which buttons
 * are tapped. Lines go to the user's own hub (/v1/event, its watch-events.log) and nowhere else.
 * Only fixed labels are recorded, never what was said, typed or read.
 */
final class Usage {
    private static final ArrayDeque<String> waiting = new ArrayDeque<>();
    private static final ExecutorService sender = Executors.newSingleThreadExecutor();
    private static String screen = "";
    private static long since;

    private Usage() {}

    /** A screen came up; the one before it is recorded with how long it was shown. */
    static void screen(Context context, String name) {
        if (name.equals(screen)) return;
        leave();
        screen = name; since = SystemClock.elapsedRealtime();
        add("screen", name, -1);
        if (waiting.size() >= 12) flush(context);
    }
    /** The app left the display. */
    static void pause(Context context) { leave(); screen = ""; flush(context); }
    static void tap(Context context, String label) { add("tap", label(label), -1); }
    static void event(Context context, String name) { add("event", name, -1); flush(context); }

    /** Button titles that carry someone's content are recorded by kind, not by their words. */
    static String label(String title) {
        String t = title == null ? "" : title.replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) return "(row)";
        if (t.startsWith("Open ")) return "Open attachment";
        return t.length() > 32 ? t.substring(0, 32) : t;
    }
    static String line(String app, String kind, String name, String on, long ms) {
        return "usage app=" + app + " " + kind + "=\"" + name.replace('"', '\'') + "\""
            + (on.isEmpty() || on.equals(name) ? "" : " on=" + on) + (ms >= 0 ? " ms=" + ms : "");
    }

    private static void leave() {
        if (!screen.isEmpty()) add("left", screen, SystemClock.elapsedRealtime() - since);
    }
    private static synchronized void add(String kind, String name, long ms) {
        String app = BuildConfig.FIXED_AGENT.isEmpty() ? "migos" : BuildConfig.FIXED_AGENT;
        if (waiting.size() >= 300) waiting.poll();
        waiting.add(line(app, kind, name, screen, ms));
    }
    private static synchronized List<String> take() {
        List<String> lines = new ArrayList<>(waiting); waiting.clear(); return lines;
    }
    private static synchronized void giveBack(List<String> lines) {
        for (int i = lines.size() - 1; i >= 0 && waiting.size() < 300; i--) waiting.addFirst(lines.get(i));
    }
    private static void flush(Context context) {
        Context app = context.getApplicationContext();
        sender.execute(() -> {
            List<String> lines = take();
            if (lines.isEmpty()) return;
            int sent = 0;
            try {
                SharedPreferences prefs = app.getSharedPreferences("hub", Context.MODE_PRIVATE);
                HubConnection hub = new HubConnection(prefs.getString("url", ""), prefs.getString("token", ""), BuildConfig.DEBUG);
                for (; sent < lines.size(); sent++)
                    hub.request("/v1/event", new JSONObject().put("text", lines.get(sent)).toString());
            } catch (Exception e) { giveBack(lines.subList(sent, lines.size())); }   // kept for the next flush
        });
    }
}
