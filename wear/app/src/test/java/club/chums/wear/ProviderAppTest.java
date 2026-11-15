package club.chums.wear;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProviderAppTest {
    @Test public void providerAvailabilityIsNotConfusedWithHubConnectivity() {
        assertEquals("Needs attention", MainActivity.providerStatus("limited"));
        assertEquals("Needs attention", MainActivity.providerStatus("blocked"));
        assertEquals("Agent offline", MainActivity.providerStatus("offline"));
        assertEquals("Working", MainActivity.providerStatus("working"));
        assertEquals("Connected", MainActivity.providerStatus("idle"));
    }
    @Test public void fixedProviderOverridesEverySavedOrRequestedAgent() {
        for (String requested : new String[]{"", "noodle", "squint", "ray", "stretch", "unknown"}) {
            assertEquals(BuildConfig.FIXED_AGENT.isEmpty() ? requested : BuildConfig.FIXED_AGENT,
                AgentVoice.forApp(requested));
        }
        if (!BuildConfig.FIXED_AGENT.isEmpty()) {
            assertFalse(AgentVoice.name(AgentVoice.forApp("")).isEmpty());
            assertNotEquals("club.chums.wear", BuildConfig.APPLICATION_ID);
        }
    }
}
