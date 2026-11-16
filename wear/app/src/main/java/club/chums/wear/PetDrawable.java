package club.chums.wear;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import java.io.IOException;

/** Original characters redrawn as six distinct poses on a 3-by-2 atlas. */
final class PetDrawable extends Drawable implements Animatable {
    private final Bitmap artwork;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect source = new Rect();
    private int pose, frame;
    private boolean running;
    private final Runnable advance = new Runnable() {
        @Override public void run() {
            if (!isRunning() || pose != 3) return;
            frame = PetPose.next(frame);
            invalidateSelf();
            scheduleSelf(this, SystemClock.uptimeMillis() + 240);
        }
    };
    PetDrawable(Context context, String agent, String state) {
        String id = switch (agent) {
            case "squint", "ray", "peeps", "stretch" -> agent;
            default -> "noodle";
        };
        try (var stream = context.getAssets().open("migo-" + id + "-poses.png")) {
            artwork = BitmapFactory.decodeStream(stream);
            if (artwork == null || artwork.getWidth() < 3 || artwork.getHeight() < 2)
                throw new IOException("Invalid sprite atlas");
        } catch (IOException e) { throw new IllegalStateException("Migo artwork unavailable", e); }
        pose(state);
    }
    void pose(String state) {
        int next = PetPose.frame(state);
        if (pose == next) return;
        pose = next; frame = next;
        unscheduleSelf(advance);
        if (isRunning() && pose == 3) scheduleSelf(advance, SystemClock.uptimeMillis() + 240);
        invalidateSelf();
    }
    @Override public int getIntrinsicWidth() { return artwork.getWidth() / 3; }
    @Override public int getIntrinsicHeight() { return artwork.getHeight() / 2; }
    @Override public void draw(Canvas canvas) {
        int column = frame % 3, row = frame / 3;
        source.set(column * artwork.getWidth() / 3, row * artwork.getHeight() / 2,
                (column + 1) * artwork.getWidth() / 3, (row + 1) * artwork.getHeight() / 2);
        canvas.drawBitmap(artwork, source, getBounds(), paint);
    }
    @Override public void start() {
        if (running || !ValueAnimator.areAnimatorsEnabled()) return;
        running = true;
        if (pose == 3) scheduleSelf(advance, SystemClock.uptimeMillis() + 240);
    }
    @Override public void stop() { running = false; unscheduleSelf(advance); frame = pose; invalidateSelf(); }
    @Override public boolean isRunning() { return running && ValueAnimator.areAnimatorsEnabled(); }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
