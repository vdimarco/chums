package club.chums.wear;

import androidx.wear.protolayout.ActionBuilders;
import androidx.wear.protolayout.LayoutElementBuilders;
import org.junit.Test;
import static org.junit.Assert.*;

public class MigosTileTest {
    private static String words(LayoutElementBuilders.Column layout, int index) {
        return ((LayoutElementBuilders.Text) layout.getContents().get(index)).getText().getValue();
    }
    @Test public void tileShowsTheLastKnownTaskAndTwoShortcuts() {
        var layout = (LayoutElementBuilders.Column) MigosTileService.layout("club.chums.wear", "Working", "Fix the build").getContents().get(0);
        assertEquals(7, layout.getContents().size());
        assertEquals(BuildConfig.APP_TITLE, words(layout, 0));
        assertEquals("Last known: Working", words(layout, 1));
        assertEquals("Fix the build", words(layout, 2));
        String[][] shortcuts = {{"4", "voice", "Live voice"}, {"6", "history", "History"}};
        for (String[] shortcut : shortcuts) {
            var box = (LayoutElementBuilders.Box) layout.getContents().get(Integer.parseInt(shortcut[0]));
            assertTrue(box.getHeight() instanceof androidx.wear.protolayout.DimensionBuilders.DpProp height && height.getValue() >= 48);
            var click = box.getModifiers().getClickable();
            var target = ((ActionBuilders.LaunchAction) click.getOnClick()).getAndroidActivity();
            assertEquals("club.chums.wear", target.getPackageName());
            assertEquals(MainActivity.class.getName(), target.getClassName());
            assertEquals(shortcut[1], click.getId());
            var extra = (ActionBuilders.AndroidStringExtra) target.getKeyToExtraMapping().get("tile_action");
            assertEquals(click.getId(), extra.getValue());
            assertEquals(shortcut[2], ((LayoutElementBuilders.Text) box.getContents().get(0)).getText().getValue());
        }
    }
    @Test public void tileSaysSoWhenNoTaskIsKnown() {
        var layout = (LayoutElementBuilders.Column) MigosTileService.layout("club.chums.wear", "", "").getContents().get(0);
        assertEquals("Open the app to see the task", words(layout, 1));
    }
}
