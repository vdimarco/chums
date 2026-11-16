package club.chums.wear;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/** The call indicator: level bars while audio flows, a turning arc while the agent works. */
final class VoiceOrb extends View {
    private static final int REST = 0, LISTENING = 1, SPEAKING = 2, WAITING = 3;
    private static final float[] BARS = {.3f, .65f, 1f, .6f, .25f};
    private final Skin skin = Skin.NOW;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private int mode = REST;

    VoiceOrb(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    /** Accepts the call's status line, so the indicator always matches the words beside it. */
    void mode(String status) {
        int next = status == null ? REST : status.startsWith("Listening") ? LISTENING : status.startsWith("Speaking") ? SPEAKING
            : status.startsWith("Thinking") || status.startsWith("Connecting") || status.startsWith("Waiting")
              || "thinking".equals(status) || "connecting".equals(status) ? WAITING : REST;
        if (next != mode) { mode = next; invalidate(); }
    }
    @Override protected void onDraw(Canvas canvas) {
        boolean moving = mode != REST && ValueAnimator.areAnimatorsEnabled();
        float time = moving ? SystemClock.uptimeMillis() / 1000f : 0;
        float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(getWidth(), getHeight()) / 2f;
        float stroke = Math.max(2f, r * (skin.outlined && !"monospace".equals(skin.body) ? .03f : .045f));
        float pulse = mode == SPEAKING ? .03f * (float) Math.sin(time * 6) : 0;
        int tone = mode == REST ? skin.muted : skin.accent;
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(stroke); paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(tone); paint.setAlpha(46);
        canvas.drawCircle(cx, cy, r * (.95f + pulse) - stroke, paint);
        paint.setAlpha(mode == WAITING ? 60 : 110);
        float mid = r * (.77f + pulse / 2);
        canvas.drawCircle(cx, cy, mid, paint);
        if (mode == WAITING) {
            paint.setAlpha(255); box.set(cx - mid, cy - mid, cx + mid, cy + mid);
            canvas.drawArc(box, time * 220 % 360, 90, false, paint);
        }
        float disc = r * .56f;
        paint.setStyle(Paint.Style.FILL); paint.setColor(mode == REST ? skin.surface : skin.accent); paint.setAlpha(255);
        if ("monospace".equals(skin.body)) {
            box.set(cx - disc, cy - disc, cx + disc, cy + disc); canvas.drawRoundRect(box, disc * .3f, disc * .3f, paint);
        } else canvas.drawCircle(cx, cy, disc, paint);
        paint.setColor(mode == REST ? skin.muted : skin.onAccent);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(disc * .11f);
        float gap = disc * .24f, tall = disc * .56f;
        for (int i = 0; i < BARS.length; i++) {
            float level = switch (mode) {
                case LISTENING -> BARS[i] * (.75f + .25f * (float) Math.sin(time * 3 + i));
                case SPEAKING -> .3f + .7f * Math.abs((float) Math.sin(time * 7 + i * 1.3f));
                case WAITING -> .12f;
                default -> BARS[i] * .5f;
            };
            float x = cx + (i - 2) * gap, half = Math.max(disc * .04f, tall * level);
            canvas.drawLine(x, cy - half, x, cy + half, paint);
        }
        if (moving && isShown()) postInvalidateOnAnimation();
    }
}
