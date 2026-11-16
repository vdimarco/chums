package club.chums.wear;
import org.junit.Test;
import static org.junit.Assert.*;
public class PcmBufferTest {
    @Test public void backpressureWraparoundAndInterruption() {
        PcmBuffer b = new PcmBuffer(6);
        assertTrue(b.add(new byte[]{1,2,3,4})); b.remove(2);
        assertTrue(b.add(new byte[]{5,6,7,8}));
        assertFalse(b.add(new byte[]{9,10}));
        assertArrayEquals(new byte[]{3,4,5,6,7,8}, b.peek(20));
        b.clear(); assertEquals(0, b.size());
        assertTrue(b.add(new byte[]{9,10}));
        assertArrayEquals(new byte[]{9,10}, b.peek(2));
        assertFalse(b.add(new byte[]{11}));
    }
}
