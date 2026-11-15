package club.chums.wear;

import android.content.Context;
import android.graphics.*;
import java.io.*;

final class PetAnimationChecks {
    static void run(Context context) {
        for (String agent : new String[]{"noodle", "squint", "ray", "stretch", "peeps"}) run(context, agent);
    }
    private static void run(Context context, String agent) {
        PetDrawable face = new PetDrawable(context, agent, "idle");
        face.setBounds(0, 0, 200, 200);
        Bitmap frame = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        Bitmap strip = Bitmap.createBitmap(1000, 240, Bitmap.Config.ARGB_8888);
        Canvas preview = new Canvas(strip); preview.drawColor(Color.BLACK);
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG); label.setColor(Color.WHITE); label.setTextSize(18);
        String[] states = {"Idle", "Listening…", "Thinking…", "Speaking…", "Call ended"};
        int[] previous = null;
        for (int i = 0; i < states.length; i++) {
            face.pose(states[i]); frame.eraseColor(Color.TRANSPARENT); face.draw(new Canvas(frame));
            int[] pixels = new int[40000]; frame.getPixels(pixels, 0, 200, 0, 0, 200, 200);
            if (java.util.Arrays.stream(pixels).allMatch(p -> p == 0)) throw new AssertionError("Empty sprite: " + states[i]);
            if (previous != null && java.util.Arrays.equals(previous, pixels)) throw new AssertionError("Expression did not change");
            previous = pixels;
            preview.drawBitmap(frame, i * 200, 0, null); preview.drawText(states[i], i * 200 + 30, 220, label);
        }
        Runnable[] scheduled = new Runnable[1];
        face.setCallback(new android.graphics.drawable.Drawable.Callback() {
            public void invalidateDrawable(android.graphics.drawable.Drawable who) { }
            public void scheduleDrawable(android.graphics.drawable.Drawable who, Runnable task, long when) { scheduled[0] = task; }
            public void unscheduleDrawable(android.graphics.drawable.Drawable who, Runnable task) { scheduled[0] = null; }
        });
        face.pose("Speaking…");
        frame.eraseColor(Color.TRANSPARENT); face.draw(new Canvas(frame));
        int[] talking = new int[40000]; frame.getPixels(talking, 0, 200, 0, 0, 200, 200);
        face.start();
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            if (scheduled[0] == null) throw new AssertionError("Talking frame not scheduled");
            scheduled[0].run();
            frame.eraseColor(Color.TRANSPARENT); face.draw(new Canvas(frame));
            int[] next = new int[40000]; frame.getPixels(next, 0, 200, 0, 0, 200, 200);
            if (java.util.Arrays.equals(talking, next)) throw new AssertionError("Talking artwork did not change");
        }
        if (android.animation.ValueAnimator.areAnimatorsEnabled() && !face.isRunning()) throw new AssertionError("Animation did not start");
        face.stop();
        if (face.isRunning() || scheduled[0] != null) throw new AssertionError("Animation did not stop");
        face.setAlpha(0); frame.eraseColor(Color.TRANSPARENT); face.draw(new Canvas(frame));
        int[] transparent = new int[40000]; frame.getPixels(transparent, 0, 200, 0, 0, 200, 200);
        if (java.util.Arrays.stream(transparent).anyMatch(p -> p != 0)) throw new AssertionError("Alpha ignored");
        try (var file = new FileOutputStream(new File(context.getCacheDir(), agent + "-sprite-expressions.png"))) {
            strip.compress(Bitmap.CompressFormat.PNG, 100, file);
        } catch (IOException e) { throw new IllegalStateException(e); }
        frame.recycle(); strip.recycle();
    }
}
