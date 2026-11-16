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
    static int background() {
        return switch (BuildConfig.FIXED_AGENT) {
            case "noodle" -> 0xff101112;
            case "squint" -> 0xff190b05;
            case "stretch" -> Color.BLACK;
            default -> Color.BLACK;
        };
    }
    static int ink() { return "squint".equals(BuildConfig.FIXED_AGENT) ? 0xffffedd5 : Color.WHITE; }
    static int accent() {
        return switch (BuildConfig.FIXED_AGENT) {
            case "noodle" -> 0xfffff2d5;
            case "squint" -> 0xffcf642f;
            case "stretch" -> 0xffeeeeec;
            default -> 0xff80e1d6;
        };
    }
    private final Bitmap artwork;
    private final Paint paint = new Paint(3);
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
        if ("noodle".equals(agent)) {
            text("codex", 20, 0xfffffaf0, "sans-serif-medium", 55, 23, 98, 28);
            status = text("", 9, 0xffb6b4a9, "sans-serif", 60, 203, 88, 14);
            icon(R.drawable.ic_provider_settings, "Connection settings", 0xffd0cfc8, Color.TRANSPARENT, 24, 153, 24, 48, 48, settings);
            action(">_  New task", "Talk to Codex", 13, 0xff171713, 0xfffff2d5, 27, 23, 154, 112, 48, talk);
            icon(R.drawable.ic_provider_history, "Conversation history", 0xfff4eddd, 0xff292a29, 26, 143, 154, 48, 48, history);
        } else if ("squint".equals(agent)) {
            text("Claude", 21, 0xffffeed6, "serif", 21, 46, 87, 29);
            status = text("", 9, 0xffc3a88b, "sans-serif", 23, 79, 80, 14);
            icon(R.drawable.ic_provider_settings, "Connection settings", 0xffe0c5a8, Color.TRANSPARENT, 24, 91, 9, 48, 48, settings);
            icon(R.drawable.ic_voice_mic, "Talk to Claude", 0xff251308, 0xffffedcd, 30, 132, 154, 56, 56, talk);
            action("History", "Conversation history", 12, 0xffffe4c5, 0xaa421d0c, 26, 25, 160, 87, 48, history);
        } else {
            text("grok", 25, Color.WHITE, "sans-serif-black", 65, 23, 78, 32);
            status = text("", 9, 0xffa5a5a5, "sans-serif", 61, 57, 86, 14);
            icon(R.drawable.ic_voice_mic, "Talk to Grok", Color.WHITE, 0xff090909, 30, 139, 154, 56, 56, talk);
            icon(R.drawable.ic_provider_history, "Conversation history", 0xffe9e9e7, 0xff181919, 26, 23, 166, 48, 48, history);
            icon(R.drawable.ic_provider_settings, "Connection settings", 0xffe9e9e7, 0xff181919, 26, 20, 33, 48, 48, settings);
        }
        task = action("Checking task status", "Open current task", 12, ink(),
            "squint".equals(agent) ? 0xee32180e : 0xee17191a, 14, 24, 94, 160, 48, history);
        task.setMaxLines(2);
        task.setEllipsize(android.text.TextUtils.TruncateAt.END);
        task.setPadding(12, 0, 12, 0);
    }

    private TextView text(String value, int size, int color, String font, int x, int y, int w, int h) {
        TextView view = new TextView(getContext()); view.setText(value); view.setTextSize(size);
        view.setTextColor(color); view.setGravity(Gravity.CENTER); view.setIncludeFontPadding(false);
        if (size > 9) view.setAutoSizeTextTypeUniformWithConfiguration(9, size, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
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
        int inset = "History".equals(value) ? Math.round(8 * getResources().getDisplayMetrics().density) : 0;
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x337f9ba3),
            new android.graphics.drawable.InsetDrawable(shape, 0, inset, 0, inset), null));
        view.setOnClickListener(v -> { v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); run.run(); });
        return view;
    }
    private void icon(int resource, String description, int ink, int fill, int radius,
                      int x, int y, int w, int h, Runnable run) {
        ImageButton view = new ImageButton(getContext()); view.setImageResource(resource);
        view.setImageTintList(ColorStateList.valueOf(ink)); view.setContentDescription(description);
        int padding = Math.round((resource == R.drawable.ic_voice_mic ? 15 : 17) * getResources().getDisplayMetrics().density);
        view.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        GradientDrawable shape = new GradientDrawable(); shape.setColor(fill);
        shape.setCornerRadius(radius * getResources().getDisplayMetrics().density);
        shape.setStroke(1, fill == Color.TRANSPARENT ? Color.TRANSPARENT : 0x337b7a74);
        int inset = Math.round((resource == R.drawable.ic_voice_mic ? 0 : 6) * getResources().getDisplayMetrics().density);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x337f7f7f),
            new android.graphics.drawable.InsetDrawable(shape, inset), null));
        view.setPadding(padding, padding, padding, padding);
        addView(view); boxes.add(new float[]{x,y,w,h});
        view.setOnClickListener(v -> { v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); run.run(); });
    }
    private int[] bounds(int index, int width, int height) {
        float scale = Math.min(width, height) / 228f;
        float ox = (width - 208 * scale) / 2, oy = (height - 228 * scale) / 2;
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
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.BLACK);
        float side = Math.min(getWidth(), getHeight());
        float left = (getWidth()-side)/2, top = (getHeight()-side)/2;
        canvas.drawBitmap(artwork, null, new RectF(left, top, left+side, top+side), paint);
    }
}
