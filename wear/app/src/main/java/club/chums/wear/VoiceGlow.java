package club.chums.wear;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Soft character halo with a voice-status orb at its upper right. */
final class VoiceGlow extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Shader halo, orb;
    private float cx, cy, radius, ox, oy, orbRadius;
    private boolean listening;
    VoiceGlow(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void listening(boolean value) { if (listening != value) { listening = value; invalidate(); } }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        radius = Math.min(w, h) * .49f; cx = w * .5f; cy = h * .5f;
        ox = w * .76f; oy = h * .21f; orbRadius = Math.min(w, h) * .18f;
        halo = new RadialGradient(cx, cy, radius, new int[]{0x1846c9b9, 0x284f9fce, Color.TRANSPARENT},
            new float[]{0, .58f, 1}, Shader.TileMode.CLAMP);
        orb = new RadialGradient(ox, oy, orbRadius, new int[]{0xffd5fff5, 0xff80e1d6, 0x805795eb, Color.TRANSPARENT},
            new float[]{0, .12f, .35f, 1}, Shader.TileMode.CLAMP);
    }
    @Override protected void onDraw(Canvas canvas) {
        paint.setAlpha(listening ? 255 : 150); paint.setShader(halo);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setAlpha(listening ? 255 : 175); paint.setShader(orb);
        canvas.drawCircle(ox, oy, orbRadius, paint);
    }
}
