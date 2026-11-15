package club.chums.wear;

import androidx.wear.protolayout.ActionBuilders;
import androidx.wear.protolayout.LayoutElementBuilders;
import org.junit.Test;
import static org.junit.Assert.*;

public class MigosTileTest {
    @Test public void shortcutsLaunchTheCorrectFlowAndSelectedAgentLabel() {
        var layout = (LayoutElementBuilders.Column) MigosTileService.layout("club.chums.wear", "Hermes").getContents().get(0);
        assertEquals(3, layout.getContents().size());
        assertEquals(BuildConfig.APP_TITLE, ((LayoutElementBuilders.Text) layout.getContents().get(0)).getText().getValue());
        for (int index : new int[]{2}) {
            var box = (LayoutElementBuilders.Box) layout.getContents().get(index);
            var click = box.getModifiers().getClickable();
            var target = ((ActionBuilders.LaunchAction) click.getOnClick()).getAndroidActivity();
            assertEquals("club.chums.wear", target.getPackageName());
            assertEquals(MainActivity.class.getName(), target.getClassName());
            assertEquals(index == 2 ? "voice" : "agent", click.getId());
            var extra = (ActionBuilders.AndroidStringExtra) target.getKeyToExtraMapping().get("tile_action");
            assertEquals(click.getId(), extra.getValue());
            var text = (LayoutElementBuilders.Text) box.getContents().get(0);
            assertEquals(index == 2 ? "Live voice" : "Talk to Hermes", text.getText().getValue());
        }
    }
}
