package club.chums.wear;

import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.wear.tiles.TileService;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.protolayout.*;
import com.google.common.util.concurrent.ListenableFuture;
import static androidx.wear.protolayout.DimensionBuilders.dp;
import static androidx.wear.protolayout.DimensionBuilders.sp;

/** Local shortcuts only: rendering the tile never wakes the cloud agents. */
public final class MigosTileService extends TileService {
    @Override protected ListenableFuture<TileBuilders.Tile> onTileRequest(RequestBuilders.TileRequest request) {
        String selected = AgentVoice.forApp(getSharedPreferences("hub", MODE_PRIVATE).getString("agent", "noodle"));
        String name = switch (selected) {
            case "noodle" -> "Codex";
            case "squint" -> "Claude Code";
            case "ray" -> "Hermes";
            case "stretch" -> "Grok";
            default -> selected;
        };
        TileBuilders.Tile tile = new TileBuilders.Tile.Builder().setResourcesVersion("1")
            .setTileTimeline(new TimelineBuilders.Timeline.Builder().addTimelineEntry(
                new TimelineBuilders.TimelineEntry.Builder().setLayout(
                    new LayoutElementBuilders.Layout.Builder().setRoot(layout(getPackageName(), name)).build()).build()).build()).build();
        return CallbackToFutureAdapter.getFuture(result -> { result.set(tile); return "ai migos tile"; });
    }
    @Override protected ListenableFuture<ResourceBuilders.Resources> onTileResourcesRequest(RequestBuilders.ResourcesRequest request) {
        return CallbackToFutureAdapter.getFuture(result -> {
            result.set(new ResourceBuilders.Resources.Builder().setVersion("1").build()); return "ai migos resources";
        });
    }
    static LayoutElementBuilders.Box layout(String packageName, String agent) {
        var content = new LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text(BuildConfig.APP_TITLE, 18, 0xff80e1d6))
            .addContent(new LayoutElementBuilders.Spacer.Builder().setHeight(dp(12)).build())
            .addContent(shortcut(packageName, "Live voice", "voice", 0xff80e1d6, 0xff142327)).build();
        return new LayoutElementBuilders.Box.Builder().setWidth(DimensionBuilders.expand()).setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER).addContent(content).build();
    }
    private static LayoutElementBuilders.Text text(String value, int size, int color) {
        return new LayoutElementBuilders.Text.Builder().setText(value).setMaxLines(1)
            .setFontStyle(new LayoutElementBuilders.FontStyle.Builder().setSize(sp(size))
                .setColor(ColorBuilders.argb(color)).build()).build();
    }
    private static LayoutElementBuilders.Box shortcut(String pkg, String title, String action, int background, int foreground) {
        var target = new ActionBuilders.AndroidActivity.Builder().setPackageName(pkg)
            .setClassName(MainActivity.class.getName())
            .addKeyToExtraMapping("tile_action", ActionBuilders.stringExtra(action)).build();
        return new LayoutElementBuilders.Box.Builder().setWidth(dp(164)).setHeight(dp(48))
            .addContent(text(title, 14, foreground))
            .setModifiers(new ModifiersBuilders.Modifiers.Builder()
                .setSemantics(new ModifiersBuilders.Semantics.Builder().setContentDescription(title).build())
                .setBackground(new ModifiersBuilders.Background.Builder().setColor(ColorBuilders.argb(background))
                    .setCorner(new ModifiersBuilders.Corner.Builder().setRadius(dp(24)).build()).build())
                .setClickable(new ModifiersBuilders.Clickable.Builder().setId(action)
                    .setOnClick(new ActionBuilders.LaunchAction.Builder().setAndroidActivity(target).build()).build()).build()).build();
    }
}
