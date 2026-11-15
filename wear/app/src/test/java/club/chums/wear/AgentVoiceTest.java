package club.chums.wear;
import org.junit.Test;
import static org.junit.Assert.*;
public class AgentVoiceTest {
    @Test public void onlyConfiguredAgentIdentitiesAreAccepted() {
        assertEquals("Codex", AgentVoice.name("noodle"));
        assertEquals("Claude", AgentVoice.name("squint"));
        assertEquals("Hermes", AgentVoice.name("ray"));
        assertEquals("Grok", AgentVoice.name("stretch"));
        assertThrows(IllegalArgumentException.class, () -> AgentVoice.name("peeps"));
        assertThrows(IllegalArgumentException.class, () -> AgentVoice.name("unknown"));
    }
}
