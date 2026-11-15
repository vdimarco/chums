package club.chums.wear;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.*;
import android.view.View;
import java.io.*;
import org.json.JSONObject;

final class LiveContentChecks {
    static void run(Instrumentation test) throws Exception {
        AgentVoiceChecks.run(test.getTargetContext(), false, false);
        try { WatchCard.decode(new ByteArrayInputStream(new byte[]{1, 2, 3})); throw new AssertionError("Invalid image accepted"); }
        catch (IOException expected) { }
        try { WatchCard.decode(new ByteArrayInputStream(new byte[2 * 1024 * 1024 + 1])); throw new AssertionError("Oversized image accepted"); }
        catch (IOException expected) { }
        try (var input = test.getTargetContext().getAssets().open("migo-noodle-poses.png")) {
            Bitmap decoded = WatchCard.decode(input);
            if (decoded.getWidth() > 600 || decoded.getHeight() > 600) throw new AssertionError("Image not sampled");
            decoded.recycle();
        }
        var screen = test.startActivitySync(new Intent(test.getTargetContext(), LiveVoiceActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("migo", "noodle"));
        try {
            var update = LiveVoiceActivity.class.getDeclaredMethod("liveUpdate", int.class, JSONObject.class); update.setAccessible(true);
            var field = LiveVoiceActivity.class.getDeclaredField("card"); field.setAccessible(true);
            JSONObject content = new JSONObject().put("title", "Demo forecast").put("body", "Montreal · Tomorrow\n12 °C · Light rain\nExample data for layout testing");
            update.invoke(screen, 0, new JSONObject().put("kind", "working").put("text", "Checking forecast…"));
            test.waitForIdleSync();
            update.invoke(screen, 0, new JSONObject().put("kind", "done").put("text", "Here's the forecast.").put("card", content));
            test.waitForIdleSync();
            test.runOnMainSync(() -> {
                try {
                    WatchCard card = (WatchCard) field.get(screen);
                    if (card.getVisibility() != View.VISIBLE) throw new AssertionError("Card not visible");
                    View view = screen.getWindow().getDecorView();
                    Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888); view.draw(new Canvas(bitmap));
                    try (var file = new FileOutputStream(new File(test.getTargetContext().getCacheDir(), "live-card.png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, file); }
                    bitmap.recycle();
                    card.show(new JSONObject().put("title", "").put("body", "bad"));
                    if (card.getVisibility() != View.GONE) throw new AssertionError("Invalid card shown");
                } catch (Exception e) { throw new IllegalStateException(e); }
            });
        } finally { test.runOnMainSync(screen::finish); }
    }
}
