package club.chums.wear;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class MigoSwipeTest {
    @Test public void verticalSwipesChooseWithoutTreatingTapsOrHorizontalMovementAsSelection() {
        assertEquals(1, MainActivity.swipeStep(4, -60, 24));
        assertEquals(-1, MainActivity.swipeStep(4, 60, 24));
        assertEquals(0, MainActivity.swipeStep(2, 3, 24));
        assertEquals(0, MainActivity.swipeStep(80, 30, 24));

    }
}
