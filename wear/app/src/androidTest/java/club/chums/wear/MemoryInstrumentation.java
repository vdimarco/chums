package club.chums.wear;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Runs on the watch without enabling its microphone. */
public final class MemoryInstrumentation extends Instrumentation {
    private Bundle options;
    @Override public void onCreate(Bundle args) { super.onCreate(args); options = args; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (options != null && "true".equals(options.getString("task_summary"))) {
                TaskSummaryChecks.run();
                result.putString("stream", "PASS: task routing, approvals, idle, missing data and offline status.\n");
                finish(-1, result); return;
            }
            if (options != null && "true".equals(options.getString("live_content"))) {
                LiveContentChecks.run(this);
                result.putString("stream", "PASS: live progress/cards, cancellation, native layout, image limits.\n");
                finish(-1, result); return;
            }
            if (options != null && "true".equals(options.getString("pet_animation"))) {
                runOnMainSync(() -> PetAnimationChecks.run(getTargetContext()));
                result.putString("stream", "PASS: sprite poses, animation lifecycle, and alpha.\n");
                finish(-1, result); return;
            }
            if (options != null && "true".equals(options.getString("design_capture"))) {
                for (Class<?> type : new Class<?>[]{MainActivity.class, LiveVoiceActivity.class}) {
                    android.app.Activity screen = startActivitySync(new android.content.Intent(getTargetContext(), type).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("migo", "noodle"));
                    waitForIdleSync(); Thread.sleep(500);
                    runOnMainSync(() -> {
                        android.view.View view = screen.getWindow().getDecorView();
                        if (view.getWidth() < 1 || view.getHeight() < 1) throw new AssertionError("Watch layout unavailable");
                        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(view.getWidth(), view.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
                        view.draw(new android.graphics.Canvas(bitmap));
                        try (var file = new FileOutputStream(new File(getTargetContext().getCacheDir(), type.getSimpleName() + "-design.png"))) {
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, file);
                        } catch (IOException e) { throw new IllegalStateException(e); }
                        bitmap.recycle(); screen.finish();
                    });
                }
                result.putString("stream", "PASS: rendered native home and call layouts without microphone access.\n");
                finish(-1, result); return;
            }
            if (options != null && "true".equals(options.getString("agent_voice"))) {
                android.app.Activity screen = startActivitySync(new android.content.Intent(getTargetContext(), MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                runOnMainSync(() -> screen.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
                try { result.putString("stream", AgentVoiceChecks.run(getTargetContext(), "true".equals(options.getString("live_agents")), "true".equals(options.getString("only_claude")))); }
                finally { runOnMainSync(() -> screen.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)); }
                finish(-1, result); return;
            }
            File root = new File(getTargetContext().getCacheDir(), "memory-check-" + UUID.randomUUID()); root.mkdirs();
            try (ServerSocket server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))) {
                server.setSoTimeout(10000);
                CompletableFuture<Boolean> first = new CompletableFuture<>();
                Thread stub = new Thread(() -> {
                    try {
                        for (int code : new int[]{200}) {
                            try (Socket socket = server.accept()) {
                                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                                int length = 0; String line;
                                while ((line = in.readLine()) != null && !line.isEmpty()) {
                                    if (line.toLowerCase().startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
                                }
                                char[] chars = new char[length]; int offset = 0;
                                while (offset < length) { int n = in.read(chars, offset, length - offset); if (n < 0) throw new IOException(); offset += n; }
                                if (!new String(chars).contains("durable test")) throw new AssertionError("Wrong outbox content");
                                byte[] body = "{\"queued\":true}".getBytes(StandardCharsets.UTF_8);
                                socket.getOutputStream().write(("HTTP/1.1 " + code + " Test\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                                socket.getOutputStream().write(body); socket.getOutputStream().flush();
                            }
                            if (code == 503) first.complete(true);
                        }
                    } catch (Throwable e) { first.completeExceptionally(e); }
                }); stub.start();
                HubConnection hub = new HubConnection("http://127.0.0.1:" + server.getLocalPort(), "test-token", true);
                VoiceMemory one = new VoiceMemory(root, new HubConnection("http://127.0.0.1:1", "test-token", true), s -> {});
                one.save(new JSONObject().put("role", "user").put("text", "durable test").put("call", "test").put("item", "one"));
                one.close();
                if (new File(root, "voice-memory").listFiles().length == 0) throw new AssertionError("Failed write lost");
                VoiceMemory two = new VoiceMemory(root, hub, s -> {});
                long end = System.currentTimeMillis() + 12000;
                while (new File(root, "voice-memory").listFiles().length > 0 && System.currentTimeMillis() < end) Thread.sleep(100);
                two.close(); stub.join(1000);
                if (new File(root, "voice-memory").listFiles().length != 0) throw new AssertionError("Retry not acknowledged");
            }
            // Exercise the installed watch's real transcript path with synthetic, explicitly labelled test text.
            var prefs = getTargetContext().getSharedPreferences("hub", 0);
            HubConnection hub = new HubConnection(prefs.getString("url", ""), prefs.getString("token", ""), true);
            String marker = "physical watch check " + UUID.randomUUID().toString().substring(0, 8);
            VoiceMemory real = new VoiceMemory(root, hub, s -> {});
            real.save(new JSONObject().put("role", "user").put("call", "watch-cloud-verification").put("item", "cloud-marker-1")
                .put("text", "Integration test only, not a preference: " + marker));
            long end = System.currentTimeMillis() + 35000;
            while (new File(root, "voice-memory").listFiles().length > 0 && System.currentTimeMillis() < end) Thread.sleep(100);
            real.close();
            if (new File(root, "voice-memory").listFiles().length != 0) throw new AssertionError("Watch could not reach hub");
            JSONObject session = new JSONObject(hub.request("/v1/realtime/session", "{}"));
            if (!session.optString("value").startsWith("ek_")) throw new AssertionError("No ephemeral voice credential");
            boolean found = false;
            end = System.currentTimeMillis() + 45000;
            while (!found && System.currentTimeMillis() < end) {
                JSONObject recalled = new JSONObject(hub.request("/v1/memory/recall", new JSONObject().put("query", marker).toString()));
                found = recalled.optString("context").contains(marker);
                if (!found) Thread.sleep(3000);
            }
            if (!found) throw new AssertionError("New watch transcript not recalled");
            result.putString("stream", "PASS: offline outbox retained; restart retry acknowledged; watch transcript reached cloud; watch obtained voice credential and recalled shared memory.\n");
            finish(-1, result);
        } catch (Throwable e) { result.putString("stream", "FAIL: " + e.getClass().getSimpleName() + "\n"); finish(1, result); }
    }
}
