package club.chums.wear;

import android.util.AtomicFile;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.json.JSONObject;

/** Durable transcript outbox: retry acknowledged-by-id writes after reconnecting. */
final class VoiceMemory {
    private final File directory;
    private final HubConnection hub;
    private final Consumer<String> status;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    VoiceMemory(File files, HubConnection hub, Consumer<String> status) {
        directory = new File(files, "voice-memory"); directory.mkdirs();
        this.hub = hub; this.status = status;
        worker.scheduleWithFixedDelay(this::flush, 0, 30, TimeUnit.SECONDS);
    }
    void save(JSONObject turn) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest((turn.getString("call") + "/" + turn.getString("item") + "/" + turn.getString("role")).getBytes(StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder(); for (byte b : hash) name.append(String.format("%02x", b));
        AtomicFile file = new AtomicFile(new File(directory, name + ".json"));
        var out = file.startWrite();
        try { out.write(turn.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(out); }
        catch (Exception e) { file.failWrite(out); throw e; }
        worker.execute(this::flush);
    }
    private void flush() {
        File[] files = directory.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return;
        for (File file : files) {
            try {
                JSONObject result = new JSONObject(hub.request("/v1/memory/turn", new String(new AtomicFile(file).readFully(), StandardCharsets.UTF_8)));
                if (!result.optBoolean("queued")) throw new IllegalStateException();
                new AtomicFile(file).delete();
                status.accept("Transcript sent · syncing");
            } catch (Exception e) { status.accept("Saved on watch · waiting to sync"); return; }
        }
    }
    void close() { worker.shutdown(); }
}
