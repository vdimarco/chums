package club.chums.wear;

/** Reading order in the generated three-column sprite atlases. */
final class PetPose {
    static int frame(String state) {
        String normalized = state.toLowerCase(java.util.Locale.ROOT).replace("…", "").trim();
        if (normalized.startsWith("waiting for ")) return 2;
        return switch (normalized) {
            case "working", "busy", "thinking", "connecting", "recalling" -> 2;
            case "waiting", "listening", "mic muted" -> 1;
            case "speaking", "done" -> 3;
            case "failed", "blocked", "limited", "call ended", "agent unavailable" -> 5;
            default -> 0;
        };
    }
    static int next(int frame) { return frame == 3 ? 4 : frame == 4 ? 3 : frame; }
}
