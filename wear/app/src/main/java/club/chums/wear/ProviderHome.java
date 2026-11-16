package club.chums.wear;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import java.util.ArrayList;

/**
 * The provider dial. The current task is the centre of the screen, one talk button leads the
 * bottom row, and a bezel ring reports state: a sweeping arc while working, amber when the
 * agent needs you, dashed when the hub is unreachable.
 */
final class ProviderHome extends FrameLayout {
    static final int IDLE = 0, WORKING = 1, ATTENTION = 2, OFFLINE = 3;
    static int background() { return Skin.NOW.bg; }
    static int ink() { return Skin.NOW.ink; }
    static int accent() { return Skin.NOW.accent; }

    private final Skin skin = Skin.NOW;
    private final String agent;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final ArrayList<float[]> boxes = new ArrayList<>();
    private Shader glow;
    private int state = IDLE;
    final TextView status;
    final TextView task;

    ProviderHome(Context context, String agent, Runnable talk, Runnable history, Runnable settings) {
        super(context); setWillNotDraw(false); this.agent = agent;
        String word = switch (agent) { case "noodle" -> "codex"; case "squint" -> "Claude"; default -> "grok"; };
        TextView title = Ui.text(context, word, "stretch".equals(agent) ? 21 : "noodle".equals(agent) ? 16 : 19, skin.ink, skin.displayFace());
        if ("stretch".equals(agent)) title.setLetterSpacing(-.02f);
        place(title, 54, 16, 120, 28);
        status = Ui.text(context, "", 10, skin.muted, skin.bodyFace());
        place(status, 44, 44, 140, 15);

        task = Ui.text(context, "Checking task status", "noodle".equals(agent) ? 12 : 13, skin.ink, skin.bodyFace());
        task.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); task.setMaxLines(3);
        task.setEllipsize(TextUtils.TruncateAt.END); task.setLineSpacing(Ui.dp(context, 2), 1f);
        int side = Ui.dp(context, "stretch".equals(agent) ? 22 : 14);
        task.setPadding(side, Ui.dp(context, 6), side, Ui.dp(context, 6));
        task.setBackground(Ui.shape(context, Ui.TONAL, "stretch".equals(agent) ? 37 : skin.card));
        task.setContentDescription("Open current task"); task.setFocusable(true);
        // The card keeps its text node for accessibility; announce it as a button.
        task.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.setClassName("android.widget.Button");
            }
        });
        task.setOnClickListener(v -> { v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); history.run(); });
        place(task, 28, 64, 172, 74);

        place(Ui.icon(context, R.drawable.ic_provider_history, "Conversation history", Ui.TONAL, skin.icon, 14, history), 28, 148, 48, 48);
        ImageButton mic = Ui.icon(context, R.drawable.ic_voice_mic, "Talk to " + AgentVoice.name(agent), Ui.PRIMARY,
            30, 16, talk);
        place(mic, 84, 148, 60, 60);
        place(Ui.icon(context, R.drawable.ic_provider_settings, "Connection settings", Ui.TONAL, skin.icon, 14, settings), 152, 148, 48, 48);
    }

    /** Hub and agent state, shown on the bezel ring. */
    void state(int next) { if (state != next) { state = next; invalidate(); } }

    private void place(View view, int x, int y, int w, int h) { addView(view); boxes.add(new float[]{x, y, w, h}); }
    private int[] bounds(int index, int width, int height) {
        float scale = Math.min(width, height) / 228f;
        float ox = (width - 228 * scale) / 2, oy = (height - 228 * scale) / 2;
        float[] b = boxes.get(index);
        float minimum = getChildAt(index).isClickable() ? 48 * getResources().getDisplayMetrics().density : 0;
        float w = Math.max(minimum, b[2] * scale), h = Math.max(minimum, b[3] * scale);
        int x = Math.round(ox + (b[0] + b[2] / 2) * scale - w / 2);
        int y = Math.round(oy + (b[1] + b[3] / 2) * scale - h / 2);
        return new int[]{x, y, x + Math.round(w), y + Math.round(h)};
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        for (int i = 0; i < getChildCount(); i++) {
            int[] b = bounds(i, width, height);
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(b[2] - b[0], MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(b[3] - b[1], MeasureSpec.EXACTLY));
        }
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int i = 0; i < getChildCount(); i++) {
            int[] b = bounds(i, right - left, bottom - top);
            getChildAt(i).layout(b[0], b[1], b[2], b[3]);
        }
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float side = Math.min(w, h);
        glow = "squint".equals(agent) ? new RadialGradient(w / 2f, h / 2f + side * .58f, side * .6f,
            (skin.accent & 0x00ffffff) | 0x48000000, skin.accent & 0x00ffffff, Shader.TileMode.CLAMP) : null;
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(skin.bg);
        float side = Math.min(getWidth(), getHeight()), unit = side / 228f;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        paint.setPathEffect(null); paint.setShader(null); paint.setStyle(Paint.Style.FILL);
        if (glow != null) { paint.setShader(glow); canvas.drawCircle(cx, cy, side / 2, paint); paint.setShader(null); }
        if ("noodle".equals(agent)) {
            // A faint terminal grid.
            paint.setColor(0xff232426);
            float step = 12 * unit, radius = side / 2 - 8 * unit;
            for (float x = cx % step; x < getWidth(); x += step) for (float y = cy % step; y < getHeight(); y += step)
                if (Math.hypot(x - cx, y - cy) < radius) canvas.drawCircle(x, y, unit, paint);
        }
        float stroke = ("stretch".equals(agent) ? 2f : 3f) * unit, inset = 4 * unit + stroke / 2;
        oval.set(cx - side / 2 + inset, cy - side / 2 + inset, cx + side / 2 - inset, cy + side / 2 - inset);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(stroke);
        paint.setStrokeCap("noodle".equals(agent) ? Paint.Cap.BUTT : Paint.Cap.ROUND);
        paint.setColor(state == ATTENTION ? Skin.WARN : skin.ring);
        if (state == OFFLINE) paint.setPathEffect(new DashPathEffect(new float[]{2 * unit, 7 * unit}, 0));
        canvas.drawOval(oval, paint);
        paint.setPathEffect(null);
        if (state == WORKING) {
            boolean moving = ValueAnimator.areAnimatorsEnabled();
            float angle = moving ? (SystemClock.uptimeMillis() % 2400) * 360f / 2400f : 0;
            paint.setColor("stretch".equals(agent) ? skin.ink : skin.label);
            canvas.drawArc(oval, angle - 90, 80, false, paint);
            if (moving && isShown()) postInvalidateOnAnimation();
        }
    }
}
