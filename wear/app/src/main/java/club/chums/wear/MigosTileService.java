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
    /** Keep the last task that the app showed, and redraw the tile when it changes. */
    static void remember(android.content.Context context, String status, String title) {
        var prefs = context.getSharedPreferences("hub", MODE_PRIVATE);
        if (status.equals(prefs.getString("tile_status", "")) && title.equals(prefs.getString("tile_title", ""))) return;
        prefs.edit().putString("tile_status", status).putString("tile_title", title).apply();
        TileService.getUpdater(context).requestUpdate(MigosTileService.class);
    }
    @Override protected ListenableFuture<TileBuilders.Tile> onTileRequest(RequestBuilders.TileRequest request) {
        var prefs = getSharedPreferences("hub", MODE_PRIVATE);
        TileBuilders.Tile tile = new TileBuilders.Tile.Builder().setResourcesVersion("1")
            .setTileTimeline(new TimelineBuilders.Timeline.Builder().addTimelineEntry(
                new TimelineBuilders.TimelineEntry.Builder().setLayout(
                    new LayoutElementBuilders.Layout.Builder().setRoot(layout(getPackageName(),
                        prefs.getString("tile_status", ""), prefs.getString("tile_title", ""))).build()).build()).build()).build();
        return CallbackToFutureAdapter.getFuture(result -> { result.set(tile); return "ai migos tile"; });
    }
    @Override protected ListenableFuture<ResourceBuilders.Resources> onTileResourcesRequest(RequestBuilders.ResourcesRequest request) {
        return CallbackToFutureAdapter.getFuture(result -> {
            result.set(new ResourceBuilders.Resources.Builder().setVersion("1").build()); return "ai migos resources";
        });
    }
    /** Name, the last known task, then the two shortcuts. All rows stay inside the round screen. */
    static LayoutElementBuilders.Box layout(String packageName, String status, String title) {
        boolean known = !status.isBlank();
        var content = new LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text(BuildConfig.APP_TITLE, 16, WatchUi.ink()))
            .addContent(text(known ? "Last known: " + status : "Open the app to see the task", 11, WatchUi.signal()))
            .addContent(text(known ? title : "", 12, WatchUi.muted()))
            .addContent(new LayoutElementBuilders.Spacer.Builder().setHeight(dp(8)).build())
            .addContent(shortcut(packageName, "Live voice", "voice", 148, WatchUi.accent(), WatchUi.onAccent()))
            .addContent(new LayoutElementBuilders.Spacer.Builder().setHeight(dp(6)).build())
            .addContent(shortcut(packageName, "History", "history", 116, WatchUi.raised(), WatchUi.ink())).build();
        return new LayoutElementBuilders.Box.Builder().setWidth(DimensionBuilders.expand()).setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(new ModifiersBuilders.Modifiers.Builder().setBackground(new ModifiersBuilders.Background.Builder()
                .setColor(ColorBuilders.argb(WatchUi.background())).build()).build())
            .addContent(content).build();
    }
    private static LayoutElementBuilders.Text text(String value, int size, int color) {
        return new LayoutElementBuilders.Text.Builder().setText(value).setMaxLines(1)
            .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
            .setFontStyle(new LayoutElementBuilders.FontStyle.Builder().setSize(sp(size))
                .setColor(ColorBuilders.argb(color)).build()).build();
    }
    private static LayoutElementBuilders.Box shortcut(String pkg, String title, String action, int width, int background, int foreground) {
        var target = new ActionBuilders.AndroidActivity.Builder().setPackageName(pkg)
            .setClassName(MainActivity.class.getName())
            .addKeyToExtraMapping("tile_action", ActionBuilders.stringExtra(action)).build();
        return new LayoutElementBuilders.Box.Builder().setWidth(dp(width)).setHeight(dp(48))
            .addContent(text(title, 14, foreground))
            .setModifiers(new ModifiersBuilders.Modifiers.Builder()
                .setSemantics(new ModifiersBuilders.Semantics.Builder().setContentDescription(title).build())
                .setBackground(new ModifiersBuilders.Background.Builder().setColor(ColorBuilders.argb(background))
                    .setCorner(new ModifiersBuilders.Corner.Builder().setRadius(dp(WatchUi.radius())).build()).build())
                .setClickable(new ModifiersBuilders.Clickable.Builder().setId(action)
                    .setOnClick(new ActionBuilders.LaunchAction.Builder().setAndroidActivity(target).build()).build()).build()).build();
    }
}
