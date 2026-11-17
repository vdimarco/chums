package club.chums.wear;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProviderPagerTest {
    @Test public void aFlingMovesOnePageAndStopsAtTheEnds() {
        assertEquals(1, ProviderPager.target(0, 900, 3));
        assertEquals(2, ProviderPager.target(1, 900, 3));
        assertEquals(2, ProviderPager.target(2, 900, 3));
        assertEquals(0, ProviderPager.target(1, -900, 3));
        assertEquals(0, ProviderPager.target(0, -900, 3));
    }
    @Test public void aSlowDragSettlesOnTheNearestPage() {
        assertEquals(0, ProviderPager.nearest(200, 456, 3));
        assertEquals(1, ProviderPager.nearest(240, 456, 3));
        assertEquals(2, ProviderPager.nearest(5000, 456, 3));
        assertEquals(0, ProviderPager.nearest(100, 0, 3));
    }
}
