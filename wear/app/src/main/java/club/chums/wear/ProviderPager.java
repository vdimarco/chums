package club.chums.wear;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/** Full-screen pages side by side. A sideways swipe moves one page; on the first page it leaves the app. */
final class ProviderPager extends HorizontalScrollView {
    interface Listener { void shown(int page); }
    private final LinearLayout row;
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Listener listener;
    private int page, startPage;
    private boolean flung;

    ProviderPager(Context context, int first, Listener listener, View... pages) {
        super(context);
        this.listener = listener; page = Math.max(0, Math.min(first, pages.length - 1));
        setHorizontalScrollBarEnabled(false); setOverScrollMode(OVER_SCROLL_NEVER); setFillViewport(true);
        setBackgroundColor(WatchUi.background());
        row = new LinearLayout(context);
        for (View view : pages) row.addView(view);
        addView(row, new LayoutParams(-2, -1));
    }
    static int target(int start, int velocity, int count) { return Math.max(0, Math.min(count - 1, start + Integer.signum(velocity))); }
    static int nearest(int scroll, int width, int count) {
        return width <= 0 ? 0 : Math.max(0, Math.min(count - 1, Math.round(scroll / (float) width)));
    }
    int page() { return page; }
    void show(int next, boolean smooth) {
        page = Math.max(0, Math.min(row.getChildCount() - 1, next));
        if (smooth) smoothScrollTo(page * getWidth(), 0); else scrollTo(page * getWidth(), 0);
        invalidate(); listener.shown(page);
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        for (int i = 0; i < row.getChildCount(); i++) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) row.getChildAt(i).getLayoutParams();
            params.width = width; params.height = height;
        }
        super.onMeasure(widthSpec, heightSpec);
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) scrollTo(page * (right - left), 0);
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { startPage = page; flung = false; }
        return super.onInterceptTouchEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) { startPage = page; flung = false; }
        boolean handled = super.onTouchEvent(event);
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && !flung)
            show(nearest(getScrollX(), getWidth(), row.getChildCount()), true);
        return handled;
    }
    @Override public void fling(int velocity) {
        flung = true; show(target(startPage, velocity, row.getChildCount()), true);
    }
    /** One dot for each page, at the bottom centre where a round screen is widest for its height. */
    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        int count = row.getChildCount();
        float radius = WatchUi.dp(getContext(), 2), gap = WatchUi.dp(getContext(), 8);
        float x = getScrollX() + getWidth() / 2f - gap * (count - 1) / 2f, y = getHeight() - WatchUi.dp(getContext(), 7);
        int shown = nearest(getScrollX(), getWidth(), count);
        for (int i = 0; i < count; i++) {
            dot.setColor(i == shown ? WatchUi.ink() : (WatchUi.muted() & 0xffffff) | 0x80000000);
            canvas.drawCircle(x + i * gap, y, radius, dot);
        }
    }
    @Override protected void onScrollChanged(int l, int t, int oldl, int oldt) { super.onScrollChanged(l, t, oldl, oldt); invalidate(); }
}
