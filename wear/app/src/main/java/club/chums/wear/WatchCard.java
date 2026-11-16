package club.chums.wear;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.JSONObject;

/** Native text and image content; never executes agent-supplied markup. */
final class WatchCard extends LinearLayout {
    private final TextView title, body, imageStatus;
    private final ImageView image;
    private Call download;
    private int revision;
    private final OkHttpClient images = new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(15, TimeUnit.SECONDS).dns(host -> {
            var addresses = Dns.SYSTEM.lookup(host);
            for (InetAddress address : addresses) {
                byte[] bytes = address.getAddress();
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()
                        || (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc)) throw new UnknownHostException("Image must be public");
            }
            return addresses;
        }).build();
    WatchCard(Context context) {
        super(context); setOrientation(VERTICAL); setGravity(Gravity.CENTER);
        setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable background = new GradientDrawable(); background.setColor(0xff151c22); background.setCornerRadius(dp(14)); setBackground(background);
        title = text(16, 0xff80e1d6); title.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body = text(12, Color.WHITE);
        image = new ImageView(context); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(image, new LayoutParams(-1, dp(110)));
        imageStatus = text(10, 0xffb4bec6); clear();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(int size, int color) {
        TextView text = new TextView(getContext()); text.setTextSize(size); text.setTextColor(color);
        text.setGravity(Gravity.CENTER); text.setPadding(0, dp(2), 0, dp(2)); addView(text); return text;
    }
    void clear() { revision++; if (download != null) download.cancel(); download = null; image.setImageDrawable(null); setVisibility(GONE); }
    void show(JSONObject card) {
        clear();
        String heading = card.optString("title"), words = card.optString("body"), url = card.optString("imageUrl");
        if (heading.isBlank() || heading.length() > 80 || words.length() > 1200) return;
        title.setText(heading); body.setText(words); body.setVisibility(words.isBlank() ? GONE : VISIBLE);
        image.setVisibility(GONE); imageStatus.setVisibility(GONE); setVisibility(VISIBLE);
        if (url.isBlank()) return;
        imageStatus.setText("Loading image…"); imageStatus.setVisibility(VISIBLE);
        final int expected = revision;
        try {
            URI uri = URI.create(url);
            if (url.length() > 2048 || !"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
                throw new IllegalArgumentException();
            // A separate client sends no hub credentials or cookies to image hosts.
            download = images.newCall(new Request.Builder().url(url).build());
            download.enqueue(new Callback() {
                public void onFailure(Call call, IOException error) { failed(expected); }
                public void onResponse(Call call, Response response) {
                    try (response) {
                        if (!response.isSuccessful() || response.body() == null) throw new IOException();
                        Bitmap bitmap = decode(response.body().byteStream());
                        post(() -> {
                            if (revision != expected) { bitmap.recycle(); return; }
                            image.setImageBitmap(bitmap); image.setContentDescription(heading); image.setVisibility(VISIBLE); imageStatus.setVisibility(GONE);
                        });
                    } catch (Exception error) { failed(expected); }
                }
            });
        } catch (IllegalArgumentException error) { failed(expected); }
    }
    private void failed(int expected) { post(() -> { if (revision == expected) imageStatus.setText("Image unavailable"); }); }
    static Bitmap decode(InputStream input) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) {
            if (data.size() + count > 2 * 1024 * 1024) throw new IOException("Image too large");
            data.write(buffer, 0, count);
        }
        byte[] bytes = data.toByteArray(); BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0 || (long) options.outWidth * options.outHeight > 40_000_000) throw new IOException("Invalid image");
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 600) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) throw new IOException("Invalid image");
        return bitmap;
    }
    @Override protected void onDetachedFromWindow() { clear(); super.onDetachedFromWindow(); }
}
