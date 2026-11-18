package club.chums.wear;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class UsageTest {
    @Test public void labelsNeverCarryContent() {
        assertEquals("(row)", Usage.label(""));
        assertEquals("Open attachment", Usage.label("Open quarterly numbers.pdf"));
        assertEquals("Live voice", Usage.label("Live voice"));
        assertEquals(32, Usage.label("x".repeat(80)).length());
    }
    @Test public void linesAreOneRecordEach() {
        assertEquals("usage app=squint tap=\"Live voice\" on=history", Usage.line("squint", "tap", "Live voice", "history", -1));
        assertEquals("usage app=noodle left=\"home\" ms=4200", Usage.line("noodle", "left", "home", "home", 4200));
    }
}
