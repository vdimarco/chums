package club.chums.wear;
import org.junit.Test;
import static org.junit.Assert.*;
public class PetPoseTest {
    @Test public void voiceStatesSelectDifferentArtwork() {
        assertEquals(0, PetPose.frame("idle"));
        assertEquals(0, PetPose.frame("Ready to talk"));
        assertEquals(1, PetPose.frame("Listening…"));
        assertEquals(1, PetPose.frame("Mic muted"));
        assertEquals(2, PetPose.frame("Waiting for Noodle…"));
        assertEquals(2, PetPose.frame("Connecting…"));
        assertEquals(2, PetPose.frame(" THINKING… "));
        assertEquals(3, PetPose.frame("Speaking…"));
        assertEquals(5, PetPose.frame("Agent unavailable"));
        assertEquals(5, PetPose.frame("Call ended"));
    }
    @Test public void onlyTheTwoTalkingFramesAlternate() {
        assertEquals(4, PetPose.next(3));
        assertEquals(3, PetPose.next(4));
        for (int frame : new int[]{0, 1, 2, 5}) assertEquals(frame, PetPose.next(frame));
    }
}
