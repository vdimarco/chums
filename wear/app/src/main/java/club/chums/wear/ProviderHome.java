package club.chums.wear;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ImageButton;
import java.util.ArrayList;

/** Native controls over a provider-specific dial, scaled to the round display. */
final class ProviderHome extends FrameLayout {
    static int background() { return WatchUi.background(); }
    static int ink() { return WatchUi.ink(); }
    static int accent() { return WatchUi.signal(); }
    private final Bitmap artwork;
    private final Paint paint = new Paint(3), scrim = new Paint();
    private final ArrayList<float[]> boxes = new ArrayList<>();
    final TextView status;
    final TextView task;

    ProviderHome(Context context, String agent, Runnable talk, Runnable history, Runnable settings) {
        super(context); setWillNotDraw(false);
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 2;
        try (var stream = context.getAssets().open("providers/" + agent + ".png")) {
            artwork = BitmapFactory.decodeStream(stream, null, options);
            if (artwork == null) throw new IllegalStateException("Provider artwork could not be decoded");
        } catch (java.io.IOException e) { throw new IllegalStateException("Provider artwork is missing", e); }
        // One layout for every provider, in a 228-unit square. Each control stays inside the round screen.
        String word = switch (agent) { case "noodle" -> "codex"; case "squint" -> "Claude"; default -> "grok"; };
        String face = switch (agent) { case "noodle" -> "monospace"; case "squint" -> "serif"; default -> "sans-serif-black"; };
        text(word, 20, WatchUi.ink(), face, 54, 17, 120, 28);
        status = text("", 10, WatchUi.muted(), "sans-serif", 34, 46, 160, 16);
        task = action("Checking task status", "Open current task", 12, WatchUi.ink(),
            (WatchUi.surface() & 0xffffff) | 0xeb000000, WatchUi.radius(), 22, 78, 184, 56, history);
        task.setMaxLines(2);
        task.setEllipsize(android.text.TextUtils.TruncateAt.END);
        task.setLineSpacing(0, 1.1f);
        task.setPadding(pad(12), 0, pad(12), 0);
        icon(R.drawable.ic_provider_history, "Conversation history", WatchUi.ink(), WatchUi.raised(), 32, 152, 48, 48, history);
        icon(R.drawable.ic_voice_mic, "Talk to " + AgentVoice.name(agent), WatchUi.onAccent(), WatchUi.accent(), 86, 158, 56, 56, talk);
        icon(R.drawable.ic_provider_settings, "Connection settings", WatchUi.ink(), WatchUi.raised(), 148, 152, 48, 48, settings);
    }
    private int pad(int value) { return WatchUi.dp(getContext(), value); }

    private TextView text(String value, int size, int color, String font, int x, int y, int w, int h) {
        TextView view = new TextView(getContext()); view.setText(value); view.setTextSize(size);
        view.setTextColor(color); view.setGravity(Gravity.CENTER); view.setIncludeFontPadding(false);
        if (size > 12) view.setAutoSizeTextTypeUniformWithConfiguration(12, size, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        view.setTypeface(Typeface.create(font, Typeface.NORMAL));
        addView(view); boxes.add(new float[]{x, y, w, h}); return view;
    }
    private TextView action(String value, String description, int size, int ink, int fill, int radius,
                        int x, int y, int w, int h, Runnable run) {
        TextView view = text(value, size, ink, "sans-serif-medium", x, y, w, h);
        view.setContentDescription(description); view.setFocusable(true);
        // TextViews retain native focus and accessibility; announce these actions as buttons.
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.setClassName("android.widget.Button");
            }
        });
        GradientDrawable shape = new GradientDrawable(); shape.setColor(fill);
        shape.setCornerRadius(radius * getResources().getDisplayMetrics().density);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff), shape, null));
        view.setOnClickListener(v -> { v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); run.run(); });
        return view;
    }
    private void icon(int resource, String description, int ink, int fill, int x, int y, int w, int h, Runnable run) {
        ImageButton view = new ImageButton(getContext()); view.setImageResource(resource);
        view.setImageTintList(ColorStateList.valueOf(ink)); view.setContentDescription(description);
        view.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        // The primary circle fills its 56 dp box. A secondary circle is 40 dp in a 48 dp touch target.
        boolean primary = w > 48;
        GradientDrawable shape = new GradientDrawable(); shape.setShape(GradientDrawable.OVAL); shape.setColor(fill);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),
            new android.graphics.drawable.InsetDrawable(shape, primary ? 0 : pad(4)), null));
        int padding = pad(primary ? 15 : 14);
        view.setPadding(padding, padding, padding, padding);
        addView(view); boxes.add(new float[]{x,y,w,h});
        view.setOnClickListener(v -> { v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); run.run(); });
    }
    private int[] bounds(int index, int width, int height) {
        float scale = Math.min(width, height) / 228f;
        float ox = (width - 228 * scale) / 2, oy = (height - 228 * scale) / 2;
        float[] b = boxes.get(index);
        float minimum = getChildAt(index).isClickable() ? 48 * getResources().getDisplayMetrics().density : 0;
        float w = Math.max(minimum, b[2]*scale), h = Math.max(minimum, b[3]*scale);
        int x = Math.round(ox + (b[0]+b[2]/2)*scale - w/2);
        int y = Math.round(oy + (b[1]+b[3]/2)*scale - h/2);
        return new int[]{x,y,x+Math.round(w),y+Math.round(h)};
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        for (int i=0; i<getChildCount(); i++) {
            int[] b = bounds(i,width,height);
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(b[2]-b[0],MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(b[3]-b[1],MeasureSpec.EXACTLY));
        }
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int i=0; i<getChildCount(); i++) {
            int[] b = bounds(i,right-left,bottom-top);
            getChildAt(i).layout(b[0],b[1],b[2],b[3]);
        }
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { scrim.setShader(null); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.BLACK);
        float side = Math.min(getWidth(), getHeight());
        float left = (getWidth()-side)/2, top = (getHeight()-side)/2;
        canvas.drawBitmap(artwork, null, new RectF(left, top, left+side, top+side), paint);
        // Darken the artwork behind the name and the actions so that text stays readable.
        if (scrim.getShader() == null) {
            int c = WatchUi.background() & 0xffffff;
            scrim.setShader(new LinearGradient(0, top, 0, top+side,
                new int[]{c | 0xd1000000, c | 0x4d000000, c | 0x4d000000, c | 0xe6000000, c | 0xe6000000},
                new float[]{0, .32f, .48f, .76f, 1}, Shader.TileMode.CLAMP));
        }
        canvas.drawRect(left, top, left+side, top+side, scrim);
    }
}
