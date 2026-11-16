package club.chums.wear;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Colours, type and controls that every screen of one provider app shares. */
final class WatchUi {
    private static final String AGENT = BuildConfig.FIXED_AGENT;
    private WatchUi() { }

    static int background() {
        return switch (AGENT) { case "noodle" -> 0xff101112; case "squint" -> 0xff190b05; default -> 0xff000000; };
    }
    static int ink() {
        return switch (AGENT) { case "noodle" -> 0xfffffaf0; case "squint" -> 0xffffedd5; default -> 0xffffffff; };
    }
    static int muted() {
        return switch (AGENT) { case "noodle" -> 0xffc9c6ba; case "squint" -> 0xffe0c5a8; case "stretch" -> 0xffb9bbb6; default -> 0xffb5c3c5; };
    }
    /** Fill of the primary action. */
    static int accent() {
        return switch (AGENT) { case "noodle" -> 0xfffff2d5; case "squint" -> 0xffffedcd; case "stretch" -> 0xffeeeeec; default -> 0xff80e1d6; };
    }
    static int onAccent() {
        return switch (AGENT) { case "noodle" -> 0xff171713; case "squint" -> 0xff251308; case "stretch" -> 0xff101010; default -> 0xff142327; };
    }
    /** Text that marks a status on the background or on a surface. */
    static int signal() {
        return switch (AGENT) { case "noodle" -> 0xfffff2d5; case "squint" -> 0xfff0906a; case "stretch" -> 0xffeeeeec; default -> 0xff80e1d6; };
    }
    static int warning() { return 0xffffc58f; }
    static int danger() { return 0xffff89b1; }
    static int onDanger() { return 0xff2a0a16; }
    /** Cards and the agent's messages. */
    static int surface() {
        return switch (AGENT) { case "noodle" -> 0xff1c1d1e; case "squint" -> 0xff32180e; case "stretch" -> 0xff17191a; default -> 0xff151c22; };
    }
    /** Secondary actions. */
    static int raised() {
        return switch (AGENT) { case "noodle" -> 0xff292a29; case "squint" -> 0xff45210f; case "stretch" -> 0xff1f2122; default -> 0xff1d292b; };
    }
    /** The owner's messages. */
    static int ownBubble() {
        return switch (AGENT) { case "noodle" -> 0xff3a3b38; case "squint" -> 0xff6b3219; case "stretch" -> 0xff2c2f31; default -> 0xff1f3d3f; };
    }
    /** Corner radius of buttons and cards, in dp. */
    static int radius() { return "noodle".equals(AGENT) ? 12 : 24; }
    static Typeface titleFace() {
        return Typeface.create(switch (AGENT) {
            case "noodle" -> "monospace"; case "squint" -> "serif"; default -> "sans-serif-medium";
        }, Typeface.NORMAL);
    }

    static int dp(Context context, float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    static GradientDrawable fill(Context context, int color, float radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(context, radius)); return shape;
    }
    static Drawable pressable(Context context, int color, float radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x33ffffff), fill(context, color, radius), fill(context, 0xffffffff, radius));
    }

    /** A scrolling column that keeps its first and last rows inside a round screen. */
    static LinearLayout page(Activity activity) {
        LinearLayout content = column(activity);
        activity.setContentView((View) content.getParent());
        ((View) content.getParent()).requestFocus();
        return content;
    }
    /** The column of a page. Its parent is the scroll view to show. */
    static LinearLayout column(Context activity) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(background());
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL); content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(activity, 22), dp(activity, 24), dp(activity, 22), dp(activity, 44));
        scroll.addView(content);
        scroll.setOnGenericMotionListener((view, event) -> {
            if (event.getAction() != MotionEvent.ACTION_SCROLL) return false;
            scroll.smoothScrollBy(0, Math.round(-event.getAxisValue(MotionEvent.AXIS_SCROLL) * dp(activity, 36)));
            return true;
        });
        return content;
    }
    static TextView title(LinearLayout parent, String words) {
        TextView view = text(parent, words, 16, ink());
        view.setTypeface(titleFace());
        // The top of a round screen is narrow.
        view.setPadding(dp(parent.getContext(), 22), dp(parent.getContext(), 2), dp(parent.getContext(), 22), dp(parent.getContext(), 4));
        return view;
    }
    static TextView text(LinearLayout parent, String words, int size, int color) {
        Context context = parent.getContext();
        TextView view = new TextView(context); view.setText(words); view.setTextSize(size); view.setTextColor(color);
        view.setGravity(Gravity.CENTER); view.setPadding(0, dp(context, 4), 0, dp(context, 4));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }
    /** A block of text on a surface, such as a message or an approval request. */
    static TextView card(LinearLayout parent, CharSequence words, int fill, int gravity) {
        Context context = parent.getContext();
        TextView view = new TextView(context); view.setText(words); view.setTextSize(13); view.setTextColor(ink());
        view.setPadding(dp(context, 11), dp(context, 8), dp(context, 11), dp(context, 8));
        view.setBackground(fill(context, fill, 14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(gravity == Gravity.CENTER ? -1 : -2, -2);
        params.gravity = gravity; params.topMargin = dp(context, 6);
        if (gravity == Gravity.END) params.leftMargin = dp(context, 18);
        if (gravity == Gravity.START) params.rightMargin = dp(context, 18);
        parent.addView(view, params);
        return view;
    }
    static Button button(LinearLayout parent, String words, boolean primary, Runnable action) {
        Context context = parent.getContext();
        Button view = new Button(context); view.setText(words); view.setAllCaps(false);
        view.setMinHeight(dp(context, 48)); view.setMinimumHeight(dp(context, 48));
        view.setTextSize(14); view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        int ink = primary ? onAccent() : ink();
        view.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}}, new int[]{(ink & 0xffffff) | 0x80000000, ink}));
        view.setStateListAnimator(null);
        view.setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 4));
        view.setBackground(pressable(context, primary ? accent() : raised(), radius()));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(context, 6);
        parent.addView(view, params);
        view.setOnClickListener(v -> { v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); action.run(); });
        return view;
    }
    static View space(LinearLayout parent, int height) {
        View view = new View(parent.getContext());
        parent.addView(view, new LinearLayout.LayoutParams(-1, dp(parent.getContext(), height)));
        return view;
    }
}
