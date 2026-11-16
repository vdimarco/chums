package club.chums.wear;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared components, drawn from the build's skin. */
final class Ui {
    static final int PRIMARY = 0, TONAL = 1, OUTLINE = 2, WARN = 3, DANGER = 4;
    private Ui() { }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
    static int fill(int kind) {
        Skin s = Skin.NOW;
        return switch (kind) {
            case PRIMARY -> s.accent;
            case TONAL -> s.surface;
            case WARN -> Skin.WARN;
            case DANGER -> Skin.DANGER;
            default -> Color.TRANSPARENT;
        };
    }
    static int ink(int kind) {
        Skin s = Skin.NOW;
        return switch (kind) {
            case PRIMARY -> s.onAccent;
            case WARN -> Skin.ON_WARN;
            case DANGER -> Skin.ON_DANGER;
            default -> s.ink;
        };
    }
    static Drawable shape(Context context, int kind, float radiusDp) {
        Skin s = Skin.NOW;
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill(kind)); shape.setCornerRadius(dp(context, radiusDp));
        if (kind == OUTLINE || (kind == TONAL && s.outlined)) shape.setStroke(Math.max(1, dp(context, 1)), s.line);
        int press = (ink(kind) & 0x00ffffff) | 0x33000000;
        return new RippleDrawable(ColorStateList.valueOf(press), shape, shape);
    }
    static GradientDrawable panel(Context context, int fill, int stroke, float radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill); shape.setCornerRadius(dp(context, radiusDp));
        if (stroke != 0) shape.setStroke(Math.max(1, dp(context, 1)), stroke);
        return shape;
    }

    /** A full-width action. Disabled actions dim instead of changing colour. */
    static Button pill(Context context, String title, int kind, Runnable action) {
        Skin s = Skin.NOW;
        Button button = new Button(context) {
            @Override public void setEnabled(boolean enabled) { super.setEnabled(enabled); setAlpha(enabled ? 1f : .45f); }
        };
        button.setText(title); button.setAllCaps(false); button.setTextSize(14);
        button.setTextColor(ink(kind)); button.setTypeface(s.strongFace());
        button.setMinHeight(dp(context, 48)); button.setMinimumHeight(dp(context, 48));
        button.setPadding(dp(context, 16), dp(context, 6), dp(context, 16), dp(context, 6));
        button.setStateListAnimator(null); button.setBackground(shape(context, kind, s.radius));
        button.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); action.run(); });
        return button;
    }
    static ImageButton icon(Context context, int resource, String description, int kind, float radiusDp, float paddingDp, Runnable action) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(resource); button.setImageTintList(ColorStateList.valueOf(ink(kind)));
        button.setContentDescription(description); button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int padding = dp(context, paddingDp); button.setPadding(padding, padding, padding, padding);
        button.setBackground(shape(context, kind, radiusDp));
        button.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); action.run(); });
        return button;
    }
    static TextView text(Context context, CharSequence value, float size, int color, Typeface face) {
        TextView view = new TextView(context);
        view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setTypeface(face);
        view.setGravity(Gravity.CENTER); view.setIncludeFontPadding(false);
        return view;
    }
    /** A small status label above a line of content, in one accessible text node. */
    static CharSequence labelled(String label, int labelColor, String content, boolean displayContent) {
        Skin s = Skin.NOW;
        SpannableStringBuilder text = new SpannableStringBuilder(label);
        text.setSpan(new ForegroundColorSpan(labelColor), 0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new RelativeSizeSpan(.8f), 0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new TypefaceSpan(s.strongFace()), 0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (content == null || content.isEmpty()) return text;
        int start = text.length() + 1;
        text.append('\n').append(content);
        if (displayContent && "serif".equals(s.display))
            text.setSpan(new TypefaceSpan(s.displayFace()), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }
    /** A chat bubble: yours on the right, the agent's on the left. */
    static TextView bubble(Context context, LinearLayout parent, String words, boolean mine) {
        Skin s = Skin.NOW;
        TextView view = text(context, words, 13, mine ? s.onMine : s.ink, s.bodyFace());
        view.setGravity(Gravity.START); view.setLineSpacing(dp(context, 2), 1f);
        view.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
        float big = dp(context, Math.min(s.card, 16)), small = dp(context, Math.min(s.card, 16) / 4);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(mine ? s.mine : s.surface);
        if (!mine && s.outlined) shape.setStroke(Math.max(1, dp(context, 1)), s.line);
        shape.setCornerRadii(mine ? new float[]{big, big, big, big, small, small, big, big}
                                  : new float[]{big, big, big, big, big, big, small, small});
        view.setBackground(shape);
        if (mine) view.setContentDescription("You: " + words);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.gravity = mine ? Gravity.END : Gravity.START; params.topMargin = dp(context, 6);
        if (mine) params.leftMargin = dp(context, 22); else params.rightMargin = dp(context, 22);
        parent.addView(view, params);
        return view;
    }
    static void field(EditText input) {
        Skin s = Skin.NOW; Context context = input.getContext();
        input.setTextSize(14); input.setTextColor(s.ink); input.setHintTextColor(s.muted); input.setTypeface(s.bodyFace());
        input.setMinHeight(dp(context, 48)); input.setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8));
        input.setBackground(panel(context, s.surface, s.line, Math.min(s.radius, 16)));
    }
    static View spacer(Context context, LinearLayout parent, float heightDp) {
        View view = new View(context);
        parent.addView(view, new LinearLayout.LayoutParams(-1, dp(context, heightDp)));
        return view;
    }
}
