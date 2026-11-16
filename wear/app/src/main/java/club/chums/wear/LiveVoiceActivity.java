package club.chums.wear;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.media.audiofx.AcousticEchoCanceler;
import android.os.*;
import android.util.Base64;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;
import java.util.concurrent.*;
import okhttp3.*;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class LiveVoiceActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private final OkHttpClient client = new OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build();
    private final Object audioLock = new Object();
    private final PcmBuffer audio = new PcmBuffer(24000 * 2 * 60);
    private volatile boolean stopped, muted, ready;
    private WebSocket socket;
    private AudioRecord recorder;
    private AudioTrack speaker;
    private AcousticEchoCanceler echo;
    private AudioManager manager;
    private AudioFocusRequest focus;
    private int oldMode;
    private long writtenFrames, itemStart;
    private boolean capturing;
    private String activeResponse = "", interruptedResponse = "";
    private String item = "";
    private int contentIndex;
    private TextView status, transcript, memoryStatus;
    private HubConnection hub;
    private VoiceMemory memory;
    private final String callId = UUID.randomUUID().toString();
    private final AtomicInteger turnGeneration = new AtomicInteger();
    private final ExecutorService lookups = Executors.newSingleThreadExecutor();
    private ImageButton mute;
    private PetDrawable pet;
    private WatchCard card;
    private ImageView companion;
    private String agent;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(ProviderHome.background());
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER); column.setPadding(dp(22), dp(2), dp(22), dp(2));
        scroll.addView(column);
        setContentView(scroll);
        agent = getIntent().getStringExtra("migo");
        if (agent == null) agent = getSharedPreferences("hub", MODE_PRIVATE).getString("agent", "noodle");
        agent = AgentVoice.forApp(agent);
        try { AgentVoice.name(agent); } catch (IllegalArgumentException ignored) { agent = "noodle"; }
        text(column, AgentVoice.name(agent) + " · LIVE", 11).setTextColor(ProviderHome.accent());
        status = text(column, "Ready to talk", 16);
        companion = new ImageView(this);
        pet = new PetDrawable(this, agent, "idle");
        if (BuildConfig.FIXED_AGENT.isEmpty()) companion.setImageDrawable(pet);
        else companion.setImageResource(getApplicationInfo().icon);
        companion.setScaleType(ImageView.ScaleType.FIT_CENTER);
        companion.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        column.addView(companion, new LinearLayout.LayoutParams(-1, dp(64)));
        card = new WatchCard(this); column.addView(card, new LinearLayout.LayoutParams(-1, -2));
        transcript = text(column, "Speak naturally. You can interrupt.", 11); transcript.setMaxLines(2);
        transcript.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable bubble = new GradientDrawable(); bubble.setColor("squint".equals(BuildConfig.FIXED_AGENT) ? 0xff3c2117 : 0xff1d2229); bubble.setCornerRadius(dp(16));
        transcript.setBackground(bubble);
        transcript.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(30)));
        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER);
        column.addView(controls, new LinearLayout.LayoutParams(-1, dp(48)));
        action(controls, R.drawable.ic_voice_end, "End call and go back", 0xffff89b1, () -> { stop(); finish(); });
        mute = action(controls, R.drawable.ic_voice_play, "Start live voice call", 0xff80e1d6, this::startCall);
        memoryStatus = text(column, "Transcripts sync automatically", 9);
        memoryStatus.setTextColor(0xff8e9b9f);
        if (getIntent().getBooleanExtra("start_call", false)) startCall();
    }
    private void startCall() {
        mute.setEnabled(false);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
        else connect();
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(LinearLayout parent, String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(ProviderHome.ink());
        t.setGravity(Gravity.CENTER); t.setPadding(0, dp(2), 0, dp(2)); parent.addView(t); return t;
    }
    private ImageButton action(LinearLayout parent, int icon, String description, int color, Runnable run) {
        ImageButton button = new ImageButton(this); button.setImageResource(icon);
        button.setImageTintList(android.content.res.ColorStateList.valueOf(0xff142327));
        button.setContentDescription(description); button.setPadding(dp(13), dp(13), dp(13), dp(13));
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setShape(GradientDrawable.OVAL);
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x44ffffff), shape, null));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(48), dp(48));
        size.setMargins(dp(10), 0, dp(10), 0); parent.addView(button, size);
        button.setOnClickListener(v -> { v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); run.run(); });
        return button;
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request != 7 || stopped) return;
        if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) connect();
        else fail("Microphone permission is needed for live voice.");
    }
    private void connect() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        status.setText("Connecting…");
        pet.pose("connecting"); pet.start();
        workers.execute(() -> {
            try {
                var prefs = getSharedPreferences("hub", MODE_PRIVATE);
                hub = new HubConnection(prefs.getString("url", ""), prefs.getString("token", ""), BuildConfig.DEBUG);
                synchronized (audioLock) {
                    if (stopped) return;
                    memory = new VoiceMemory(getFilesDir(), hub, message -> main.post(() -> memoryStatus.setText(message)));
                }
                JSONObject session = new JSONObject(hub.request("/v1/realtime/session", "{}"));
                synchronized (audioLock) {
                    if (stopped) return;
                    socket = client.newWebSocket(new Request.Builder()
                        .url("wss://api.openai.com/v1/realtime?model=" + session.getString("model"))
                        .header("Authorization", "Bearer " + session.getString("value")).build(), new WebSocketListener() {
                        @Override public void onMessage(WebSocket ws, String value) { synchronized (audioLock) { if (stopped) return; socket = ws; } event(value); }
                        @Override public void onFailure(WebSocket ws, Throwable error, Response response) { fail("Voice connection lost. End and try again."); }
                        @Override public void onClosed(WebSocket ws, int code, String reason) { fail("Call ended"); }
                        @Override public void onClosing(WebSocket ws, int code, String reason) { ws.close(code, null); }
                    });
                }
                main.postDelayed(() -> { if (!ready && !stopped) fail("Connection timed out. End and try again."); }, 20000);
            } catch (Exception e) {
                fail(e instanceof java.io.IOException ? e.getMessage() + ". Check the hub and OpenAI API billing." : "Could not start live voice.");
            }
        });
    }
    private void event(String value) {
        if (stopped) return;
        try {
            JSONObject e = new JSONObject(value);
            switch (e.optString("type")) {
                case "session.created" -> send(AgentVoice.audioSession());
                case "session.updated" -> main.post(this::startAudio);
                case "input_audio_buffer.speech_started" -> { int generation = turnGeneration.incrementAndGet(); main.post(() -> { if (!stopped && generation == turnGeneration.get()) { card.clear(); companion.setVisibility(android.view.View.VISIBLE); transcript.setVisibility(android.view.View.VISIBLE); } }); interruptedResponse = activeResponse; interruptPlayback(); show("Listening…", null); }
                case "input_audio_buffer.speech_stopped" -> show("Thinking…", null);
                case "response.output_audio.delta" -> {
                    activeResponse = e.optString("response_id");
                    if (!interruptedResponse.isEmpty() && interruptedResponse.equals(activeResponse)) return;
                    byte[] pcm = Base64.decode(e.getString("delta"), Base64.DEFAULT);
                    synchronized (audioLock) {
                        if (stopped || speaker == null) return;
                        String next = e.getString("item_id");
                        if (!next.equals(item)) { item = next; itemStart = writtenFrames + audio.size() / 2; contentIndex = e.optInt("content_index"); }
                        if (!audio.add(pcm)) { fail("Audio connection is too slow. End and try again."); return; }
                    }
                    show("Speaking…", null);
                }
                case "response.output_audio_transcript.done" -> show(null, e.optString("transcript"));
                case "conversation.item.input_audio_transcription.completed" -> {
                    saveTurn("user", e.optString("item_id"), e.optString("transcript"));
                    askAgent(e.optString("transcript"));
                }
                case "response.created" -> activeResponse = e.getJSONObject("response").optString("id");
                case "response.done" -> {
                    JSONObject response = e.optJSONObject("response");
                    if (response != null && "completed".equals(response.optString("status"))) completed(response);
                    if (response != null && "failed".equals(response.optString("status"))) fail("OpenAI could not answer. Check API billing or try again.");
                }
                case "error" -> fail("OpenAI reported a voice error. End and try again; check API billing if it persists.");
            }
        } catch (Exception e) { fail("Invalid voice response. End and try again."); }
    }
    private void liveUpdate(int generation, JSONObject line) {
        main.post(() -> {
            if (stopped || generation != turnGeneration.get()) return;
            String words = line.optString("text");
            if (!words.isBlank()) transcript.setText(words);
            if ("working".equals(line.optString("kind"))) { status.setText("Thinking…"); pet.pose("thinking"); }
            JSONObject display = line.optJSONObject("card");
            if (display == null && card.getVisibility() == android.view.View.VISIBLE
                    && java.util.Set.of("done", "blocked", "question", "error").contains(line.optString("kind"))) {
                card.clear(); companion.setVisibility(android.view.View.VISIBLE); transcript.setVisibility(android.view.View.VISIBLE);
            }
            if (display != null) {
                card.show(display);
                if (card.getVisibility() == android.view.View.VISIBLE) {
                    companion.setVisibility(android.view.View.GONE);
                    transcript.setVisibility(android.view.View.GONE);
                }
            }
        });
    }
    private void saveTurn(String role, String id, String text) {
        if (text.isBlank() || id.isBlank() || memory == null) return;
        try { memory.save(new JSONObject().put("call", callId).put("item", id).put("role", role).put("text", text)); }
        catch (Exception e) { main.post(() -> memoryStatus.setText("Could not save transcript on watch")); }
    }
    private void completed(JSONObject response) throws Exception {
        JSONArray output = response.optJSONArray("output");
        if (output == null) return;
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.getJSONObject(i);
            if ("message".equals(item.optString("type"))) {
                JSONArray content = item.optJSONArray("content");
                if (content != null) for (int j = 0; j < content.length(); j++) {
                    JSONObject part = content.getJSONObject(j);
                    saveTurn("assistant", item.optString("id") + "-" + j, part.optString("transcript", part.optString("text")));
                }
            }
        }
    }
    private void askAgent(String words) {
        if (words.isBlank() || stopped) return;
        int generation = turnGeneration.get();
        show("Waiting for " + AgentVoice.name(agent) + "…", words);
        lookups.execute(() -> {
            try {
                String reply = new AgentVoice(hub::request).reply(agent, words, () -> !stopped && generation == turnGeneration.get(), line -> liveUpdate(generation, line));
                if (stopped || generation != turnGeneration.get()) return;
                show("Speaking…", reply);
                send(AgentVoice.readAloud(reply));
            } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            catch (Exception error) { if (stopped || generation != turnGeneration.get()) return; show("Agent unavailable", "Delivery not confirmed. Check Conversation history before repeating."); }
        });
    }
    @android.annotation.SuppressLint("MissingPermission")
    private void startAudio() {
        if (stopped || ready) return;
        try {
            manager = (AudioManager) getSystemService(AUDIO_SERVICE); oldMode = manager.getMode();
            AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(change -> { if (change < 0) fail("Call ended because audio focus changed."); }).build();
            if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) throw new IllegalStateException();
            manager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            int inputSize = AudioRecord.getMinBufferSize(24000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int outputSize = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (inputSize <= 0 || outputSize <= 0) throw new IllegalStateException();
            synchronized (audioLock) {
                recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 24000, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, Math.max(inputSize, 960 * 8));
                speaker = new AudioTrack.Builder().setAudioAttributes(attrs)
                        .setAudioFormat(new AudioFormat.Builder().setSampleRate(24000).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(Math.max(outputSize, 960 * 4))
                        .setTransferMode(AudioTrack.MODE_STREAM).build();
                if (recorder.getState() != AudioRecord.STATE_INITIALIZED || speaker.getState() != AudioTrack.STATE_INITIALIZED) throw new IllegalStateException();
                if (AcousticEchoCanceler.isAvailable()) { echo = AcousticEchoCanceler.create(recorder.getAudioSessionId()); if (echo != null) echo.setEnabled(true); }
                recorder.startRecording(); speaker.play(); ready = true;
            }
            workers.execute(this::capture); workers.execute(this::playback);
            mute.setImageResource(R.drawable.ic_voice_mic); mute.setContentDescription("Mute microphone"); mute.setEnabled(true); mute.setOnClickListener(v -> {
                muted = !muted; mute.setImageResource(muted ? R.drawable.ic_voice_mic_off : R.drawable.ic_voice_mic);
                mute.setContentDescription(muted ? "Unmute microphone" : "Mute microphone");
                if (muted) {
                    try { send(new JSONObject().put("type", "input_audio_buffer.clear")); }
                    catch (Exception e) { fail("Could not mute the call."); }
                }
                show(muted ? "Mic muted" : "Listening…", null);
            });
            show("Listening…", "Speak naturally. You can interrupt.");
            main.postDelayed(() -> fail("10-minute call finished. Start a new call to continue."), 10 * 60 * 1000);
        } catch (Exception e) { fail("Could not open watch audio. Check microphone permission and audio output."); }
    }
    private void capture() {
        AudioRecord input;
        synchronized (audioLock) { if (stopped) return; input = recorder; capturing = true; }
        byte[] pcm = new byte[960];
        try {
            while (!stopped) {
                int n = input.read(pcm, 0, pcm.length);
                if (n < 0) { if (!stopped) fail("Microphone stopped working."); return; }
                if (n == 0 || muted || stopped) continue;
                if (!send(new JSONObject().put("type", "input_audio_buffer.append")
                        .put("audio", Base64.encodeToString(pcm, 0, n, Base64.NO_WRAP)))) return;
            }
        } catch (Exception e) { if (!stopped) fail("Microphone stopped working."); }
        finally { synchronized (audioLock) { input.release(); if (recorder == input) recorder = null; } }
    }
    private void playback() {
        boolean speaking = false;
        try {
            while (!stopped) {
                synchronized (audioLock) {
                    if (stopped) return;
                    byte[] chunk = audio.peek(960);
                    if (chunk.length > 0) {
                        int count = speaker.write(chunk, 0, chunk.length, AudioTrack.WRITE_NON_BLOCKING);
                        if (count < 0) throw new IllegalStateException();
                        audio.remove(count); writtenFrames += count / 2; speaking = true;
                    } else if (speaking && Integer.toUnsignedLong(speaker.getPlaybackHeadPosition()) >= writtenFrames) {
                        speaking = false; show("Listening…", null);
                    }
                }
                Thread.sleep(10);
            }
        } catch (Exception e) { if (!stopped) fail("Watch speaker stopped working."); }
    }
    private void interruptPlayback() throws Exception {
        synchronized (audioLock) {
            if (stopped || speaker == null) return;
            long played = Integer.toUnsignedLong(speaker.getPlaybackHeadPosition());
            if (!item.isEmpty() && (audio.size() > 0 || played < writtenFrames)) {
                send(new JSONObject().put("type", "conversation.item.truncate").put("item_id", item)
                        .put("content_index", contentIndex).put("audio_end_ms", Math.max(0, played - itemStart) / 24));
            }
            audio.clear(); speaker.pause(); speaker.flush(); speaker.stop(); speaker.play();
            writtenFrames = 0; itemStart = 0; item = "";
        }
    }
    private boolean send(JSONObject event) {
        synchronized (audioLock) {
            if (stopped || socket == null) return false;
            if (socket.queueSize() > 24000 * 2 * 4 || !socket.send(event.toString())) { fail("Voice upload is too slow. End and try again."); return false; }
            return true;
        }
    }
    private void show(String title, String words) {
        int generation = turnGeneration.get();
        main.post(() -> { if (stopped || generation != turnGeneration.get()) return; if (title != null) { status.setText(muted ? "Mic muted" : title); pet.pose(muted ? "Mic muted" : title); } if (words != null) transcript.setText(words); });
    }
    private void fail(String message) {
        main.post(() -> { if (stopped) return; stop(); pet.pose("failed"); status.setText("Call ended"); transcript.setMaxLines(8); transcript.setLayoutParams(new LinearLayout.LayoutParams(-1, -2)); transcript.setText(message); mute.setImageResource(R.drawable.ic_voice_retry); mute.setContentDescription("Retry live voice call"); mute.setEnabled(true);
            mute.setOnClickListener(v -> {
                startActivity(new android.content.Intent(this, LiveVoiceActivity.class).putExtra("start_call", true).putExtra("migo", agent)); finish();
            }); });
    }
    private void stop() {
        if (card != null) card.clear();
        if (companion != null) companion.setVisibility(android.view.View.VISIBLE);
        if (transcript != null) transcript.setVisibility(android.view.View.VISIBLE);
        if (pet != null) pet.stop();
        synchronized (audioLock) {
            if (stopped) return;
            stopped = true;
            if (memory != null) memory.close();
            lookups.shutdownNow();
            if (socket != null) socket.cancel();
            if (recorder != null) { try { recorder.stop(); } catch (IllegalStateException ignored) { }
                if (!capturing) { recorder.release(); recorder = null; } }
            if (echo != null) { echo.release(); echo = null; }
            if (speaker != null) { speaker.release(); speaker = null; }
            audio.clear();
        }
        if (manager != null) { if (focus != null) manager.abandonAudioFocusRequest(focus); manager.setMode(oldMode); }
        workers.shutdownNow(); client.dispatcher().executorService().shutdown(); client.connectionPool().evictAll();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override protected void onResume() {
        super.onResume();
        if (!stopped) pet.start();
    }
    @Override protected void onStop() {
        if (!stopped) { stop(); pet.pose("failed"); status.setText("Call ended"); transcript.setText("You left the call. Go back to start again."); mute.setEnabled(false); }
        super.onStop();
    }
    @Override protected void onDestroy() { stop(); main.removeCallbacksAndMessages(null); super.onDestroy(); }
}
