package emu.grasscutter.server.http.handlers;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.notice.NoticeCatalog;
import emu.grasscutter.game.shop.FreeStore;
import emu.grasscutter.server.http.Router;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

/** A self-contained announcement page; no official CDN or absent asset tree is required. */
public final class AnnouncementsHandler implements Router {
    private final Supplier<FreeStore> store;

    public AnnouncementsHandler() {
        this(
                () -> {
                    var server = Grasscutter.getGameServer();
                    return server == null ? null : server.getShopSystem().getFreeStore();
                });
    }

    AnnouncementsHandler(Supplier<FreeStore> store) {
        this.store = store;
    }

    public static void sdkConfig(Context ctx) {
        var info = emu.grasscutter.config.Configuration.HTTP_INFO;
        var encryption = emu.grasscutter.config.Configuration.HTTP_ENCRYPTION;
        String address = info.accessAddress.isEmpty() ? info.bindAddress : info.accessAddress;
        int port = info.accessPort == 0 ? info.bindPort : info.accessPort;
        String url =
                (encryption.useInRouting ? "https://" : "http://")
                        + address
                        + ":"
                        + port
                        + "/hk4e/announcement/index.html?sdk_presentation_style=fullscreen&sdk_screen_transparent=true&game=hk4e";
        response(
                ctx,
                Map.of(
                        "protocol",
                        true,
                        "qr_enabled",
                        false,
                        "log_level",
                        "INFO",
                        "announce_url",
                        url,
                        "push_alias_type",
                        2,
                        "disable_ysdk_guard",
                        false,
                        "enable_announce_pic_popup",
                        false));
    }

    private static void response(Context ctx, Object data) {
        ctx.header("Cache-Control", "no-store")
                .json(Map.of("retcode", 0, "message", "OK", "data", data));
    }

    @Override
    public void applyRoutes(Javalin app) {
        for (String region : List.of("hk4e_cn", "hk4e_global")) {
            String prefix = "/common/" + region + "/announcement/api/";
            allRoutes(app, prefix + "getAnnList", ctx -> response(ctx, NoticeCatalog.get().clientList()));
            allRoutes(
                    app, prefix + "getAnnContent", ctx -> response(ctx, NoticeCatalog.get().clientContent()));
            allRoutes(
                    app, prefix + "getAlertPic", ctx -> response(ctx, Map.of("total", 0, "list", List.of())));
            allRoutes(
                    app,
                    prefix + "getAlertAnn",
                    ctx -> {
                        var active = NoticeCatalog.get().active();
                        response(
                                ctx,
                                Map.of(
                                        "alert",
                                        !active.isEmpty(),
                                        "alert_id",
                                        active.isEmpty() ? 0 : active.get(0).id(),
                                        "remind",
                                        true));
                    });
            // The desktop SDK can select V2 independently of the game shop protocol.
            // Returning the generic empty success body leaves its product lookup unready.
            for (String api : List.of("listPriceTier", "listPriceTierV2")) {
                allRoutes(
                        app,
                        "/" + region + "/mdk/shopwindow/shopwindow/" + api,
                        ctx -> {
                            var freeStore = store.get();
                            if (freeStore == null) {
                                ctx.status(503).json(Map.of("retcode", 1, "message", "Game server unavailable"));
                                return;
                            }
                            var prices = freeStore.priceTiers(region.equals("hk4e_global"));
                            // Deliberately omit query strings, account IDs and SDK tokens.
                            Grasscutter.getLogger()
                                    .info(
                                            "SDK price table: region={}, api={}, tiers={}",
                                            region,
                                            api,
                                            ((List<?>) prices.get("tiers")).size());
                            response(ctx, prices);
                        });
            }
        }
        app.get("/hk4e/announcement/index.html", AnnouncementsHandler::page);
        app.get("/hk4e/announcement/", AnnouncementsHandler::page);
    }

    private static void page(Context ctx) throws IOException {
        try (var stream = AnnouncementsHandler.class.getResourceAsStream("/gm/announcement.html")) {
            if (stream == null) throw new IOException("Announcement page is missing from the server jar");
            ctx.header("Cache-Control", "no-store")
                    .contentType("text/html; charset=utf-8")
                    .result(stream.readAllBytes());
        }
    }
}
