package club.chums.wear;

// The audio lock in LiveVoiceActivity owns this bounded PCM ring.
final class PcmBuffer {
    private final byte[] bytes;
    private int head, count;
    PcmBuffer(int capacity) { bytes = new byte[capacity]; }
    int size() { return count; }
    boolean add(byte[] incoming) {
        if (incoming.length % 2 != 0 || incoming.length > bytes.length - count) return false;
        for (byte value : incoming) bytes[(head + count++) % bytes.length] = value;
        return true;
    }
    byte[] peek(int maximum) {
        byte[] out = new byte[Math.min(maximum, count)];
        for (int i = 0; i < out.length; i++) out[i] = bytes[(head + i) % bytes.length];
        return out;
    }
    void remove(int length) {
        if (length < 0 || length > count || length % 2 != 0) throw new IllegalArgumentException("Invalid PCM write");
        head = (head + length) % bytes.length; count -= length;
    }
    void clear() { head = 0; count = 0; }
}
