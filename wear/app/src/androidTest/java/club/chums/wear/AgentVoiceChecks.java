package club.chums.wear;

import android.content.Context;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicInteger;

final class AgentVoiceChecks {
    static String run(Context context, boolean live, boolean onlyClaude) throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AgentVoice fixture = new AgentVoice((path, body) -> {
            int step = requests.getAndIncrement();
            if (step == 0) return "{\"rev\":1,\"sessions\":[{\"name\":\"noodle\"}],\"spoken\":[{\"id\":\"old\",\"agent\":\"noodle\",\"kind\":\"done\",\"text\":\"stale\"}]}";
            if (step == 1) {
                JSONObject sent = new JSONObject(body);
                if (!"noodle".equals(sent.getString("agent")) || !"fixture request".equals(sent.getString("text"))) throw new AssertionError("Wrong agent or words");
                return "{\"kind\":\"order\",\"target\":\"noodle\"}";
            }
            return "{\"rev\":2,\"spoken\":[{\"id\":\"other\",\"agent\":\"ray\",\"kind\":\"done\",\"text\":\"wrong agent\"},{\"id\":\"echo\",\"agent\":\"noodle\",\"kind\":\"echo\",\"text\":\"echo\"},{\"id\":\"new\",\"agent\":\"noodle\",\"kind\":\"done\",\"text\":\"correct reply\"}]}";
        });
        if (!"correct reply".equals(fixture.reply("noodle", "fixture request", () -> true)) || requests.get() != 3) throw new AssertionError("Reply routing");
        requests.set(0);
        AgentVoice approvals = new AgentVoice((path, body) -> switch (requests.getAndIncrement()) {
            case 0 -> "{\"rev\":1,\"sessions\":[{\"name\":\"ray\"}]}";
            case 1 -> "{\"kind\":\"order\",\"target\":\"ray\"}";
            default -> "{\"rev\":2,\"approvals\":[{\"session\":\"ray\"}]}";
        });
        if (!approvals.reply("ray", "fixture request", () -> true).startsWith("Approval is needed") || requests.get() != 3) throw new AssertionError("Approval boundary");
        AgentVoice broken = new AgentVoice((path, body) -> { throw new java.io.IOException("fixture failure"); });
        try { broken.reply("squint", "fixture", () -> true); throw new AssertionError("Failure hidden"); }
        catch (java.io.IOException expected) { }
        requests.set(0);
        java.util.List<JSONObject> updates = new java.util.ArrayList<>();
        AgentVoice cards = new AgentVoice((path, body) -> switch (requests.getAndIncrement()) {
            case 0 -> "{\"rev\":1,\"sessions\":[{\"name\":\"noodle\"}]}";
            case 1 -> "{\"kind\":\"order\",\"target\":\"noodle\"}";
            case 2 -> "{\"rev\":2,\"spoken\":[{\"id\":\"progress\",\"agent\":\"noodle\",\"kind\":\"working\",\"text\":\"Checking forecast\",\"card\":{\"title\":\"Weather\",\"body\":\"Loading\"}}]}";
            default -> "{\"rev\":3,\"spoken\":[{\"id\":\"progress\",\"agent\":\"noodle\",\"kind\":\"working\"},{\"id\":\"answer\",\"agent\":\"noodle\",\"kind\":\"done\",\"text\":\"Forecast ready\",\"card\":{\"title\":\"Weather\",\"body\":\"Fixture forecast\"}}]}";
        });
        if (!"Forecast ready".equals(cards.reply("noodle", "fixture", () -> true, updates::add)) || updates.size() != 2
                || !"Fixture forecast".equals(updates.get(1).getJSONObject("card").getString("body"))) throw new AssertionError("Live cards missing or duplicated");
        requests.set(0);
        java.util.concurrent.atomic.AtomicBoolean current = new java.util.concurrent.atomic.AtomicBoolean(true);
        AgentVoice cancelled = new AgentVoice((path, body) -> {
            int step = requests.getAndIncrement();
            if (step == 0) return "{\"rev\":1,\"sessions\":[{\"name\":\"noodle\"}]}";
            if (step == 1) return "{\"kind\":\"order\",\"target\":\"noodle\"}";
            current.set(false);
            return "{\"rev\":2,\"spoken\":[{\"id\":\"late\",\"agent\":\"noodle\",\"kind\":\"done\",\"text\":\"Stale\"}]}";
        });
        try { cancelled.reply("noodle", "fixture", current::get, line -> { throw new AssertionError("Stale update delivered"); }); throw new AssertionError("Cancellation ignored"); }
        catch (InterruptedException expected) { }
        StringBuilder report = new StringBuilder("PASS: selected-agent routing, stale/other-agent filtering, approval boundary, network failures, live cards, cancellation.\n");
        if (live) {
            var prefs = context.getSharedPreferences("hub", 0);
            HubConnection hub = new HubConnection(prefs.getString("url", ""), prefs.getString("token", ""), true);
            checkAudio(hub);
            report.append("PASS: voice session disables automatic answers and renders agent text to audio.\n");
            for (String id : onlyClaude ? new String[]{"squint"} : new String[]{"noodle", "squint", "ray", "stretch"}) {
                try {
                    String reply = new AgentVoice(hub::request).reply(id, "Watch live voice integration test. Use shared memory to recall my copper robin watch test phrase, including its four digits. Reply in one brief sentence. Do not change files or save new preferences.", () -> true);
                    report.append(AgentVoice.name(id)).append(": ").append(reply.contains("3852") ? "shared-memory recall passed" : reply.toLowerCase().matches("(?s).*(limit|quota|usage).*" ) ? "quota limited" : reply.toLowerCase().contains("authenticat") ? "authentication blocked" : reply.contains("not connected") ? "not connected" : "reply received; recall not verified").append('\n');
                } catch (Exception e) { report.append(AgentVoice.name(id)).append(": unavailable (").append(e.getClass().getSimpleName()).append(")\n"); }
            }
        }
        return report.toString();
    }
    private static void checkAudio(HubConnection hub) throws Exception {
        JSONObject credential = new JSONObject(hub.request("/v1/realtime/session", "{}"));
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        java.util.concurrent.CompletableFuture<Boolean> done = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.atomic.AtomicBoolean audio = new java.util.concurrent.atomic.AtomicBoolean();
        okhttp3.WebSocket socket = client.newWebSocket(new okhttp3.Request.Builder()
            .url("wss://api.openai.com/v1/realtime?model=" + credential.getString("model"))
            .header("Authorization", "Bearer " + credential.getString("value")).build(), new okhttp3.WebSocketListener() {
            @Override public void onMessage(okhttp3.WebSocket ws, String value) {
                try {
                    JSONObject event = new JSONObject(value);
                    switch (event.optString("type")) {
                        case "session.created" -> ws.send(AgentVoice.audioSession().toString());
                        case "session.updated" -> {
                            JSONObject detection = event.getJSONObject("session").getJSONObject("audio").getJSONObject("input").getJSONObject("turn_detection");
                            if (detection.getBoolean("create_response")) throw new AssertionError("Automatic answers still enabled");
                            ws.send(AgentVoice.readAloud("Watch agent voice route verified.").toString());
                        }
                        case "response.output_audio.delta" -> audio.set(true);
                        case "response.done" -> done.complete(audio.get() && "completed".equals(event.getJSONObject("response").optString("status")));
                        case "error" -> done.completeExceptionally(new AssertionError("Realtime event rejected: " + event.getJSONObject("error").optString("code")));
                    }
                } catch (Throwable error) { done.completeExceptionally(error); }
            }
            @Override public void onFailure(okhttp3.WebSocket ws, Throwable error, okhttp3.Response response) { done.completeExceptionally(error); }
        });
        try { if (!done.get(40, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("No agent speech audio"); }
        finally { socket.cancel(); client.dispatcher().executorService().shutdown(); client.connectionPool().evictAll(); }
    }

}
