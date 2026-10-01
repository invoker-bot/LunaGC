package emu.grasscutter.server.http.handlers;

import static emu.grasscutter.config.Configuration.SERVER;
import static java.util.Map.entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandMap;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.activity.ActivityScheduleStore;
import emu.grasscutter.game.activity.HistoricalActivityService;
import emu.grasscutter.game.gacha.BannerConfig;
import emu.grasscutter.game.gacha.GachaBanner;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ItemUseOp;
import emu.grasscutter.server.event.EventHandler;
import emu.grasscutter.server.event.game.ReceiveCommandFeedbackEvent;
import emu.grasscutter.server.http.Router;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.JsonUtils;
import emu.grasscutter.utils.lang.Language;
import io.javalin.Javalin;
import io.javalin.http.Context;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * A web GM console mounted under {@code /gm} on the dispatch HTTP server.
 *
 * <p>The reference tool this replaces (YSGM_GUI) is a C# WPF application that talks to the official
 * server's MUIP HTTP API -- signed requests to {@code /api?cmd=1116&...} with a shared secret. A
 * private server has no MUIP, so its GM surface is the command system: {@code give}, {@code
 * account}, {@code spawn} and friends. Rather than reimplementing a second, parallel set of GM
 * operations, this router drives the same {@link CommandMap} the console uses, which means anything
 * the console can do the page can do, and anything added to either shows up in both.
 *
 * <p>Routes are guarded by {@link emu.grasscutter.config.ConfigContainer.Server.GM#accessToken the
 * configured token}; a request without it gets a 403 and nothing else. An empty token means the
 * console is open to loopback with no secret at all, which is what this repo's config ships. The
 * console page itself is a single self-contained HTML file at {@code /gm/console.html} in
 * resources, so the server ships it and the browser runs it.
 *
 * <p>Besides driving the command system, the console also exposes a read-only view of the item
 * catalogue and the gacha banner table, so an operator can hand out items by name and flip banners
 * on and off without remembering ids or editing {@code data/Banners.json} by hand.
 */
public final class GmHandler implements Router {
    private static final String CONSOLE_RESOURCE = "/gm/console.html";

    /** Cap on how many item rows one request will return, however big the page asks for. */
    private static final int ITEM_PAGE_LIMIT = 200;

    /** Where the banner table lives, relative to the data directory. */
    private static final String BANNERS_FILE = "Banners.json";

    /**
     * Official-server banner opening times, keyed by version.
     *
     * <p>The banner table in {@code data/Banners.json} never records the official schedule: every row
     * in every revision of the file carries {@code beginTime = 0} and a sentinel {@code endTime =
     * 1924992000}, because a private server's gacha system activates a banner by sort order and
     * disabled flag, not by wall-clock windows. Nothing in the data therefore tells an operator which
     * of two banners ran first on the official server -- the question this table exists to answer.
     *
     * <p>These are the approximate first-half openings as month-granular approximations from the
     * official version calendar, not exact maintenance times, so the console always renders them with
     * a 约 marker next to the exact repository commit date. Trim them as better information arrives; a
     * value here only changes the label and the sort order of the history view, never the gacha
     * system itself. A version missing from this map is labelled with the commit date alone.
     */
    private static final Map<String, YearMonth> OFFICIAL_VERSION_START =
            Map.ofEntries(
                    entry("1.0", YearMonth.of(2020, 9)),
                    entry("1.1", YearMonth.of(2020, 11)),
                    entry("1.2", YearMonth.of(2021, 1)),
                    entry("1.3", YearMonth.of(2021, 2)),
                    entry("1.4", YearMonth.of(2021, 3)),
                    entry("1.5", YearMonth.of(2021, 4)),
                    entry("1.6", YearMonth.of(2021, 5)),
                    entry("2.0", YearMonth.of(2021, 7)),
                    entry("2.1", YearMonth.of(2021, 9)),
                    entry("2.2", YearMonth.of(2021, 10)),
                    entry("2.3", YearMonth.of(2021, 11)),
                    entry("2.4", YearMonth.of(2022, 1)),
                    entry("2.5", YearMonth.of(2022, 2)),
                    entry("2.6", YearMonth.of(2022, 3)),
                    entry("2.7", YearMonth.of(2022, 5)),
                    entry("2.8", YearMonth.of(2022, 7)),
                    entry("3.0", YearMonth.of(2022, 8)),
                    entry("3.1", YearMonth.of(2022, 9)),
                    entry("3.2", YearMonth.of(2022, 11)),
                    entry("3.3", YearMonth.of(2022, 12)),
                    entry("3.4", YearMonth.of(2023, 1)),
                    entry("3.5", YearMonth.of(2023, 3)),
                    entry("3.6", YearMonth.of(2023, 4)),
                    entry("3.7", YearMonth.of(2023, 5)),
                    entry("3.8", YearMonth.of(2023, 7)),
                    entry("4.0", YearMonth.of(2023, 8)),
                    entry("4.1", YearMonth.of(2023, 9)),
                    entry("4.2", YearMonth.of(2023, 11)),
                    entry("4.3", YearMonth.of(2023, 12)),
                    entry("4.4", YearMonth.of(2024, 1)),
                    entry("4.5", YearMonth.of(2024, 3)),
                    entry("4.6", YearMonth.of(2024, 4)),
                    entry("4.7", YearMonth.of(2024, 6)),
                    entry("4.8", YearMonth.of(2024, 7)),
                    entry("5.0", YearMonth.of(2024, 8)),
                    entry("5.1", YearMonth.of(2024, 10)),
                    entry("5.2", YearMonth.of(2024, 11)),
                    entry("5.3", YearMonth.of(2025, 1)),
                    entry("5.4", YearMonth.of(2025, 2)),
                    entry("5.5", YearMonth.of(2025, 3)),
                    entry("5.6", YearMonth.of(2025, 5)),
                    entry("5.7", YearMonth.of(2025, 6)),
                    entry("5.8", YearMonth.of(2025, 7)),
                    entry("6.0", YearMonth.of(2025, 9)),
                    entry("6.1", YearMonth.of(2025, 10)),
                    entry("6.2", YearMonth.of(2025, 12)),
                    entry("6.3", YearMonth.of(2026, 1)),
                    entry("6.4", YearMonth.of(2026, 2)),
                    entry("6.5", YearMonth.of(2026, 4)),
                    entry("6.6", YearMonth.of(2026, 5)),
                    entry("6.7", YearMonth.of(2026, 7)),
                    entry("7.0", YearMonth.of(2026, 8)),
                    entry("7.1", YearMonth.of(2026, 9)));

    /** Extracts the version a banner-table commit is for, when its message names one. */
    private static final Pattern VERSION_IN_SUBJECT = Pattern.compile("\\b(\\d+\\.\\d+)\\b");

    /** The pseudo-revision id used for the working-tree copy of the banner table. */
    private static final String WORKING_REVISION = "working";

    private static final String ARCHIVE_REVISION = "archive";
    private static final String BANNER_ARCHIVE_RESOURCE = "/gm/banner-history.json";

    /**
     * scheduleIds at or above this mark disabled "official archive" rows rather than live banners.
     */
    private static final int ARCHIVE_SCHEDULE_ID_BASE = 90000;

    /** Extracts the version label from an archive row's comment, e.g. "v2.4 官服归档 - ...". */
    private static final Pattern ARCHIVE_VERSION_IN_COMMENT = Pattern.compile("v(\\d+\\.\\d+) 官服归档");

    @Override
    public void applyRoutes(Javalin javalin) {
        javalin.get("/gm", GmHandler::serveConsole);
        javalin.get("/gm/", GmHandler::serveConsole);
        javalin.get("/gm/api/commands", GmHandler::listCommands);
        javalin.get("/gm/api/players", GmHandler::listPlayers);
        javalin.post("/gm/api/command", GmHandler::runCommand);
        javalin.get("/gm/api/items", GmHandler::listItems);
        javalin.get("/gm/api/equipment/options", GmHandler::equipmentOptions);
        javalin.post("/gm/api/equipment/give", GmHandler::giveEquipment);
        javalin.get("/gm/api/banners", GmHandler::listBanners);
        javalin.get("/gm/api/banners/history", GmHandler::listBannerHistory);
        javalin.post("/gm/api/banners", GmHandler::setBanner);
        javalin.get("/gm/api/activities", GmHandler::listActivities);
        javalin.post("/gm/api/activities", GmHandler::setActivity);
        javalin.get("/gm/api/shops", ctx -> shopRequest(ctx, GmShop::list));
        javalin.post("/gm/api/shops", ctx -> shopRequest(ctx, GmShop::edit));
        javalin.get("/gm/api/store/products", ctx -> shopRequest(ctx, GmShop::products));
        javalin.post("/gm/api/store/products", ctx -> shopRequest(ctx, GmShop::editProduct));
        javalin.post("/gm/api/store/purchase", ctx -> shopRequest(ctx, GmShop::purchase));
        javalin.get("/gm/api/notices", ctx -> messageRequest(ctx, GmMessages::notices));
        javalin.post("/gm/api/notices", ctx -> messageRequest(ctx, GmMessages::editNotice));
        javalin.get("/gm/api/mail", ctx -> messageRequest(ctx, GmMessages::inbox));
        javalin.post("/gm/api/mail", ctx -> messageRequest(ctx, GmMessages::sendMail));
    }

    private static void messageRequest(Context ctx, ShopRequest request) throws Exception {
        if (!authorize(ctx)) return;
        ctx.header("Cache-Control", "no-store");
        try {
            request.run(ctx);
        } catch (IllegalArgumentException | NullPointerException e) {
            ctx.status(400)
                    .json(
                            Map.of(
                                    "retcode",
                                    400,
                                    "message",
                                    e.getMessage() == null ? "公告或邮件参数不完整。" : e.getMessage()));
        } catch (Exception e) {
            Grasscutter.getLogger().error("GM announcement/mail operation failed", e);
            ctx.status(500).json(Map.of("retcode", 500, "message", "操作未能保存，请检查服务日志后重试。"));
        }
    }

    @FunctionalInterface
    private interface ShopRequest {
        void run(Context ctx) throws Exception;
    }

    private static void shopRequest(Context ctx, ShopRequest request) throws Exception {
        if (!authorize(ctx)) return;
        try {
            request.run(ctx);
        } catch (IllegalArgumentException | NullPointerException e) {
            ctx.status(400)
                    .json(
                            Map.of(
                                    "retcode", 400, "message", e.getMessage() == null ? "商城参数不完整。" : e.getMessage()));
        } catch (Exception e) {
            Grasscutter.getLogger().error("GM shop operation failed", e);
            ctx.status(500).json(Map.of("retcode", 500, "message", "商城操作未能保存，请检查服务日志。"));
        }
    }

    private static void listActivities(Context ctx) throws Exception {
        if (!authorize(ctx)) return;
        ctx.json(HistoricalActivityService.list());
    }

    private static void setActivity(Context ctx) throws Exception {
        if (!authorize(ctx)) return;
        try {
            var body = JsonUtils.decode(ctx.body(), JsonObject.class);
            if (body == null || !body.has("key") || !body.has("action"))
                throw new IllegalArgumentException("必须指定活动 key 和 action");
            String action = body.get("action").getAsString();
            ctx.json(
                    HistoricalActivityService.update(
                            body.get("key").getAsString(),
                            action,
                            ActivityScheduleStore.requestedDays(body),
                            "reschedule".equals(action) ? ActivityScheduleStore.requestedEnd(body) : null));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ctx.status(400).json(Map.of("retcode", -1, "message", e.getMessage()));
        }
    }

    /** Serves the console page; every interaction after that goes through the JSON endpoints. */
    private static void serveConsole(Context ctx) throws Exception {
        if (!authorize(ctx)) return;

        var page = FileUtils.readResource(CONSOLE_RESOURCE);
        if (page.length == 0) {
            ctx.status(404).result("GM console page is missing from this build.");
            return;
        }
        ctx.contentType("text/html; charset=utf-8");
        ctx.header("Cache-Control", "no-store");
        ctx.result(page);
    }

    /** Lists every registered command with its annotation, so the page can build its catalogue. */
    private static void listCommands(Context ctx) throws Exception {
        if (!authorize(ctx)) return;

        // Aliases share one annotation with their command, so the annotations map has a row per
        // alias as well; getHandlers is keyed by the canonical label only.
        var commands = new ArrayList<Map<String, Object>>();
        var annotations = CommandMap.getInstance().getAnnotations();
        for (var label : CommandMap.getInstance().getHandlers().keySet()) {
            Command a = annotations.get(label);
            if (a == null) continue;
            var cmd = new LinkedHashMap<String, Object>();
            cmd.put("label", label);
            cmd.put("aliases", a.aliases());
            cmd.put("usage", String.join(" | ", a.usage()));
            cmd.put("permission", a.permission());
            cmd.put("target", a.targetRequirement().name());
            cmd.put("threading", a.threading());
            commands.add(cmd);
        }
        // Sorted by label so the catalogue reads in a stable order.
        commands.sort(Comparator.comparing(m -> (String) m.get("label")));

        ctx.contentType("application/json; charset=utf-8");
        ctx.json(Map.of("retcode", 0, "commands", commands));
    }

    /** Lists online players with what the console needs to target them. */
    private static void listPlayers(Context ctx) throws Exception {
        if (!authorize(ctx)) return;

        var players = new ArrayList<Map<String, Object>>();
        for (Player player : Grasscutter.getGameServer().getPlayers().values()) {
            var entry = new LinkedHashMap<String, Object>();
            entry.put("uid", player.getUid());
            entry.put("nickname", player.getNickname());
            entry.put("level", player.getLevel());
            entry.put("worldLevel", player.getWorldLevel());
            entry.put("sceneId", player.getSceneId());
            var pos = player.getPosition();
            entry.put("position", new double[] {pos.getX(), pos.getY(), pos.getZ()});
            players.add(entry);
        }
        players.sort(Comparator.comparingInt(m -> (int) m.get("uid")));

        ctx.contentType("application/json; charset=utf-8");
        ctx.json(Map.of("retcode", 0, "players", players));
    }

    /**
     * Runs a command as the console would: {@code CommandMap.invoke(null, null, "<command> @<uid>
     * <args>")} -- a null sender is the console, which the permission handler lets do anything, and
     * an {@code @uid} argument is how the raw-message syntax picks its target.
     *
     * <p>The handler returns before the command's own output is finished when the command is
     * annotated {@code threading} ({@link CommandMap} spawns it on a bare thread), so the response is
     * settled rather than waited on: after invoke returns, the capture keeps draining until the
     * output stops moving, then whatever was collected is what the page shows.
     */
    private static void runCommand(Context ctx) throws Exception {
        if (!authorize(ctx)) return;

        var body = ctx.body();
        if (body.isBlank()) {
            ctx.status(400).json(Map.of("retcode", 400, "message", "empty body"));
            return;
        }

        JsonObject parsed;
        try {
            parsed = JsonUtils.decode(body, JsonObject.class);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("retcode", 400, "message", "body is not valid JSON"));
            return;
        }
        if (parsed == null || !parsed.has("command")) {
            ctx.status(400).json(Map.of("retcode", 400, "message", "missing 'command'"));
            return;
        }

        String command = parsed.get("command").getAsString().trim();
        if (command.isEmpty()) {
            ctx.status(400).json(Map.of("retcode", 400, "message", "empty command"));
            return;
        }

        // The web form keeps target and command separate so the page can offer a player picker. The
        // raw-message syntax takes @uid as an argument, not as the leading token -- '@uid' in the
        // label position is the 'target' command and would swallow the rest of the line -- so it goes
        // right after the command name, which is also how it is typed in-game.
        if (parsed.has("target") && !parsed.get("target").isJsonNull()) {
            String target = parsed.get("target").getAsString().trim();
            if (!target.isEmpty()) {
                int space = command.indexOf(' ');
                String rest = space < 0 ? "" : command.substring(space + 1);
                command =
                        (space < 0 ? command : command.substring(0, space))
                                + " @"
                                + target
                                + (rest.isEmpty() ? "" : " " + rest);
            }
        }

        List<String> output = CommandCapture.invokeAndCapture(command);

        ctx.contentType("application/json; charset=utf-8");
        ctx.json(Map.of("retcode", 0, "command", command, "output", output));
    }

    // ------------------------------------------------------------------
    // Item catalogue
    // ------------------------------------------------------------------

    private static void equipmentOptions(Context ctx) throws Exception {
        if (!authorize(ctx)) return;
        try {
            ctx.json(
                    Map.of(
                            "retcode",
                            0,
                            "equipment",
                            GmEquipment.options(parseIntOrDefault(ctx.queryParam("id"), 0))));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("retcode", 400, "message", e.getMessage()));
        }
    }

    private static void giveEquipment(Context ctx) throws Exception {
        if (!authorize(ctx)) return;
        try {
            GmEquipment.Request request;
            try {
                request = JsonUtils.decode(ctx.body(), GmEquipment.Request.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("装备参数不是有效的 JSON。");
            }
            if (request == null || request.target <= 0) throw new IllegalArgumentException("请填写目标 UID。");
            var player = Grasscutter.getGameServer().getPlayerByUid(request.target);
            if (player == null || !player.isOnline())
                throw new IllegalArgumentException("目标玩家未在线，请刷新在线列表。");
            // Reject the entire configuration before making any inventory changes.
            var items = GmEquipment.create(request);
            int count = GmEquipment.grant(player, items);
            ctx.json(
                    Map.of(
                            "retcode",
                            0,
                            "granted",
                            count,
                            "message",
                            "已给予 UID "
                                    + request.target
                                    + " 装备 "
                                    + request.itemId
                                    + " × "
                                    + count
                                    + "（等级 "
                                    + request.level
                                    + "）。"));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("retcode", 400, "message", e.getMessage()));
        } catch (IllegalStateException e) {
            Grasscutter.getLogger().error("GM equipment grant failed", e);
            ctx.status(500).json(Map.of("retcode", 500, "message", e.getMessage()));
        }
    }

    /**
     * Searches the loaded item table so the console can hand things out by name instead of by id.
     *
     * <p>Names come from the text maps -- one lookup per item in {@link
     * Language#getTextMapStrings()}, which is already loaded, rather than {@link
     * Language#getTextMapKey(int)}, which re-reads the 22 MB cache bin from disk on every miss. The
     * server's own configured language is not necessarily Chinese, and the console's audience reads
     * Chinese, so the CHS column is read directly.
     *
     * <p>Query parameters: {@code q} matches the Chinese name or the numeric id (as a substring),
     * {@code type} is an {@link emu.grasscutter.game.inventory.ItemType} name such as {@code
     * ITEM_WEAPON}, {@code limit} and {@code offset} page the results. The response carries the
     * filtered {@code total} so the page can tell "no matches" from "last page".
     */
    private static void listItems(Context ctx) throws Exception {
        if (!authorize(ctx)) return;

        String q = ctx.queryParam("q");
        String type = ctx.queryParam("type");
        int limit = parseIntOrDefault(ctx.queryParam("limit"), 50);
        int offset = Math.max(0, parseIntOrDefault(ctx.queryParam("offset"), 0));
        // A page big enough to be a dump is big enough to be refused.
        limit = Math.min(Math.max(1, limit), ITEM_PAGE_LIMIT);

        final String needle = q == null ? "" : q.trim().toLowerCase();
        final var strings = Language.getTextMapStrings();

        var matched = new ArrayList<Map<String, Object>>();
        for (ItemData data : GameData.getItemDataMap().values()) {
            String name = nameOf(strings, data);
            boolean named = name != null && !name.isBlank() && !name.startsWith("[N/A]");
            String displayName = named ? name : "名称待确认（" + data.getId() + "）";
            String typeName = data.getItemType() != null ? data.getItemType().name() : "ITEM_NONE";
            if (type != null && !type.isEmpty() && !type.equals(typeName)) continue;
            if (!needle.isEmpty()) {
                String hay = displayName.toLowerCase();
                if (!hay.contains(needle) && !String.valueOf(data.getId()).contains(needle)) continue;
            }

            var row = new LinkedHashMap<String, Object>();
            row.put("id", data.getId());
            row.put("name", displayName);
            row.put("missingTranslation", !named);
            row.put("type", typeName);
            row.put("rankLevel", data.getRankLevel());
            row.put("stackLimit", data.getStackLimit());
            row.put("maxLevel", data.isEquip() ? GmEquipment.maxLevel(data) : 0);
            matched.add(row);
        }

        // Stable order so paging does not reshuffle between requests.
        matched.sort(Comparator.comparingInt(m -> (int) m.get("id")));

        int total = matched.size();
        int from = Math.min(offset, total);
        int to = Math.min(from + limit, total);
        List<Map<String, Object>> page = matched.subList(from, to);

        var body = new LinkedHashMap<String, Object>();
        body.put("retcode", 0);
        body.put("total", total);
        body.put("limit", limit);
        body.put("offset", from);
        body.put("items", page);

        ctx.contentType("application/json; charset=utf-8");
        ctx.json(body);
    }

    /**
     * The CHS name of an item, or null when the text map has no row for its hash.
     *
     * <p>Avatar cards are the one place the naive lookup mislabels things: their own {@code
     * nameTextMapHash} resolves to the word "Card" (or whatever the shared card placeholder is),
     * because the character's name lives on the avatar the card grants, not on the card. So for a
     * {@link MaterialType#MATERIAL_AVATAR} item, walk its use list to the {@link
     * ItemUseOp#ITEM_USE_GAIN_AVATAR} entry, read the granted avatar's name hash from there, and fall
     * back to the card's own hash when that path does not resolve.
     */
    static String nameOf(Int2ObjectMap<Language.TextStrings> strings, ItemData data) {
        if (data.getMaterialType() == MaterialType.MATERIAL_AVATAR) {
            long avatarHash = grantedAvatarNameHash(data);
            if (avatarHash != 0) {
                String name = chsOf(strings, avatarHash);
                if (name != null) return name;
            }
        }
        return chsOf(strings, data.getNameTextMapHash());
    }

    /** The CHS string for a text-map hash, or null when the text map has no row for it. */
    private static String chsOf(Int2ObjectMap<Language.TextStrings> strings, long hash) {
        var ts = strings.get((int) hash);
        return ts != null ? ts.get("CHS") : null;
    }

    /**
     * The name hash of the avatar an avatar card grants, or 0 when the card names none.
     *
     * <p>{@code useParam[0]} is the avatar id as a string, per the excels; {@link
     * ItemData#getItemUse()} is null for items that do nothing when used.
     */
    private static long grantedAvatarNameHash(ItemData data) {
        var uses = data.getItemUse();
        if (uses == null) return 0;
        for (var use : uses) {
            if (use.getUseOp() != ItemUseOp.ITEM_USE_GAIN_AVATAR) continue;
            var params = use.getUseParam();
            if (params == null || params.length == 0) continue;
            try {
                int avatarId = Integer.parseInt(params[0]);
                var avatar = GameData.getAvatarDataMap().get(avatarId);
                return avatar != null ? avatar.getNameTextMapHash() : 0;
            } catch (NumberFormatException ignored) {
                continue;
            }
        }
        return 0;
    }

    /** Parses {@code s} as an int, falling back to {@code fallback} when it is blank or malformed. */
    private static int parseIntOrDefault(String s, int fallback) {
        if (s == null || s.isBlank()) return fallback;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------
    // Gacha banners
    // ------------------------------------------------------------------

    /**
     * Lists the banner table as the operator sees it on disk: every row of {@code Banners.json},
     * including the ones currently disabled, with its live status beside it.
     *
     * <p>{@code loaded} is whether the banner is in the gacha system's map at all -- a disabled row
     * is skipped by {@link emu.grasscutter.game.gacha.GachaSystem#load()} and never reaches it.
     * {@code active} is whether the client's wish screen would actually show it, which mirrors the
     * time-window rule in {@code createProto}: a banner is active when it is loaded and inside its
     * begin/end window, including standard banners.
     */
    private static void listBanners(Context ctx) throws Exception {
        if (!authorize(ctx)) return;

        // The live map is what decides "would this banner be shown right now".
        var live = Grasscutter.getGameServer().getGachaSystem().getGachaBanners();
        long now = System.currentTimeMillis() / 1000L;
        var strings = Language.getTextMapStrings();

        var rows = new ArrayList<Map<String, Object>>();
        for (GachaBanner banner : loadBannerRows()) {
            // The archive rows live in the same file but are history, not the table the operator
            // edits here; they are reached through the history view, so they stay out of this list.
            if (isArchivedBanner(banner)) continue;
            rows.add(bannerRowOf(strings, banner, live, now));
        }

        // Same order as the file, so flipping a row does not re-order the list under the operator.
        rows.sort(Comparator.comparingInt(m -> (int) m.get("scheduleId")));

        ctx.contentType("application/json; charset=utf-8");
        ctx.json(Map.of("retcode", 0, "banners", rows));
    }

    /**
     * One banner as the console renders it: the row's own fields, the UP item names resolved beside
     * their ids, and its live status stamped against the gacha system's current map.
     *
     * <p>{@code loaded} is whether the banner is in the gacha system's map at all -- a disabled row
     * is skipped by {@link emu.grasscutter.game.gacha.GachaSystem#load()} and never reaches it.
     * {@code active} is whether the client's wish screen would actually show it, which mirrors the
     * time-window rule in {@code createProto}: a banner is active when it is loaded and inside its
     * begin/end window, including standard banners.
     */
    private static Map<String, Object> bannerRowOf(
            Int2ObjectMap<Language.TextStrings> strings,
            GachaBanner banner,
            Int2ObjectMap<GachaBanner> live,
            long now) {
        boolean loaded = live.containsKey(banner.getScheduleId());

        var row = new LinkedHashMap<String, Object>();
        row.put("scheduleId", banner.getScheduleId());
        row.put("sortId", banner.getSortId());
        row.put("gachaType", effectiveGachaType(banner));
        row.put("bannerType", banner.getBannerType() != null ? banner.getBannerType().name() : null);
        row.put("beginTime", banner.getBeginTime());
        row.put("endTime", banner.getEndTime());
        row.put("costItemId", effectiveCostItemId(banner));
        row.put("rateUpItems4", banner.getRateUpItems4());
        row.put("rateUpItems5", banner.getRateUpItems5());
        row.put("rateUpItems4Names", upSlots(strings, banner.getRateUpItems4()));
        row.put("rateUpItems5Names", upSlots(strings, banner.getRateUpItems5()));
        for (int rarity : new int[] {5, 4, 3}) {
            int[] items = banner.getPossibleItems(rarity);
            row.put("poolItems" + rarity, items);
            row.put("poolItems" + rarity + "Names", upSlots(strings, items));
        }
        row.put("removeC6FromPool", banner.isRemoveC6FromPool());
        row.put("disabled", banner.isDisabled());
        row.put("loaded", loaded);
        row.put("active", loaded && banner.isActive(now));
        // Where this banner sat in the official version's schedule, for the history view's ordering.
        row.put("phase", phaseLabel(effectiveGachaType(banner)));
        return row;
    }

    /**
     * The gacha type that takes effect, since Gson never calls {@code onLoad()}: a row deserialised
     * straight from JSON keeps the class default of -1 when it does not state one, and the banner
     * type carries the real value for that case.
     */
    private static int effectiveGachaType(GachaBanner banner) {
        if (banner.getGachaType() >= 0) return banner.getGachaType();
        return banner.getBannerType() != null ? banner.getBannerType().gachaType : -1;
    }

    /** The cost item that takes effect, for the same reason as {@link #effectiveGachaType}. */
    private static int effectiveCostItemId(GachaBanner banner) {
        int cost = banner.getCost(1).getId();
        if (cost != 0) return cost;
        return banner.getBannerType() != null ? banner.getBannerType().costItemId : 0;
    }

    /** The UP items of one slot as id-plus-name rows, so the page can show names instead of ids. */
    private static List<Map<String, Object>> upSlots(
            Int2ObjectMap<Language.TextStrings> strings, int[] ids) {
        if (ids == null || ids.length == 0) return List.of();
        var out = new ArrayList<Map<String, Object>>(ids.length);
        for (int id : ids) out.add(upSlot(strings, id));
        return out;
    }

    /** One UP item as an id-plus-name row, looking it up through the avatar-card rule in nameOf. */
    private static Map<String, Object> upSlot(Int2ObjectMap<Language.TextStrings> strings, int id) {
        var data = GameData.getItemDataMap().get(id);
        var row = new LinkedHashMap<String, Object>();
        row.put("id", id);
        row.put("name", data != null ? nameOf(strings, data) : null);
        String kind = "unknown";
        if (data != null) {
            if (data.getItemType() == ItemType.ITEM_WEAPON) kind = "weapon";
            else if (data.getMaterialType() == MaterialType.MATERIAL_AVATAR) kind = "character";
        }
        row.put("kind", kind);
        return row;
    }

    /**
     * Where a banner's gacha type sits in a version's official schedule, so the history view can sort
     * banners into the order they ran on the official server.
     */
    private static int phaseOrder(int gachaType) {
        return switch (gachaType) {
            case 100 -> 0; // 新手祈愿: permanent, opens with the account
            case 200 -> 1; // 常驻祈愿: permanent from launch
            case 301 -> 2; // 角色活动祈愿: the version's first half
            case 302 -> 3; // 武器活动祈愿: runs alongside the first half
            case 400 -> 4; // 角色活动祈愿-2: the version's second half
            case 500 -> 5; // 集录祈愿: runs alongside the second half
            default -> 9;
        };
    }

    /** A short Chinese label for a banner's slot in the official schedule. */
    private static String phaseLabel(int gachaType) {
        return switch (gachaType) {
            case 100 -> "新手";
            case 200 -> "常驻";
            case 301 -> "上半";
            case 302 -> "武器池";
            case 400 -> "下半";
            case 500 -> "集录";
            default -> "其它";
        };
    }

    /**
     * Acts on a banner: {@code {"scheduleId": 3701, "action": "enable"|"disable"}} flips a row in the
     * live table, and {@code {"scheduleId": 3640, "action": "rerun", "commit": "f2af6609"}} copies a
     * historical banner back into the live table with a fresh window.
     *
     * <p>Both rewrites land in {@code Banners.json} and then ask the gacha system to reload, because
     * this repo's config leaves {@code gameOptions.watchGachaConfig} off -- without the watcher, the
     * file change alone would sit there until a restart. The write is atomic (temp file plus move) so
     * a crash mid-write cannot truncate the banner table, and the reload happens only after the move
     * lands.
     *
     * <p>An unknown scheduleId is a 400 rather than a silent success: the caller's list and the file
     * have diverged, and pretending the flip worked would leave the operator staring at a banner that
     * never changes.
     */
    private static synchronized void setBanner(Context ctx) throws Exception {
        if (!authorize(ctx)) return;

        var body = ctx.body();
        if (body.isBlank()) {
            ctx.status(400).json(Map.of("retcode", 400, "message", "empty body"));
            return;
        }

        JsonObject parsed;
        try {
            parsed = JsonUtils.decode(body, JsonObject.class);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("retcode", 400, "message", "body is not valid JSON"));
            return;
        }
        if (parsed == null || !parsed.has("scheduleId") || !parsed.has("action")) {
            ctx.status(400).json(Map.of("retcode", 400, "message", "missing 'scheduleId' or 'action'"));
            return;
        }

        int scheduleId;
        try {
            scheduleId = parsed.get("scheduleId").getAsBigDecimal().intValueExact();
            if (scheduleId < 0) throw new IllegalArgumentException("unassigned schedule id");
        } catch (Exception e) {
            ctx.status(400)
                    .json(Map.of("retcode", 400, "message", "'scheduleId' must be a non-negative number"));
            return;
        }

        String action = parsed.get("action").getAsString();
        if ("disable".equals(action) || "enable".equals(action)) {
            flipBannerRow(ctx, scheduleId, "disable".equals(action));
        } else if ("rerun".equals(action)) {
            if (!parsed.has("commit") || !parsed.get("commit").isJsonPrimitive()) {
                ctx.status(400)
                        .json(Map.of("retcode", 400, "message", "'action': 'rerun' needs a 'commit'"));
                return;
            }
            long now = System.currentTimeMillis() / 1000L;
            long end;
            try {
                end = BannerConfig.rerunEnd(parsed, now);
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("retcode", 400, "message", e.getMessage()));
                return;
            }
            rerunBannerRow(ctx, scheduleId, parsed.get("commit").getAsString(), now, end);
        } else if ("reschedule".equals(action)) {
            rescheduleBannerRow(ctx, scheduleId, parsed);
        } else {
            ctx.status(400)
                    .json(
                            Map.of(
                                    "retcode",
                                    400,
                                    "message",
                                    "'action' must be enable, disable, rerun or reschedule"));
        }
    }

    /** Flips {@code disabled} on one row of the live banner table. */
    private static void flipBannerRow(Context ctx, int scheduleId, boolean disable) throws Exception {
        Path file = FileUtils.getDataPath(BANNERS_FILE);
        JsonArray rows;
        try {
            rows = readBannerTable(file);
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .error("Could not read the banner table at {}: {}", file, e.getMessage());
            ctx.status(500)
                    .json(
                            Map.of(
                                    "retcode",
                                    500,
                                    "message",
                                    "could not read " + BANNERS_FILE + ": " + e.getMessage()));
            return;
        }

        boolean found = false;
        for (JsonElement el : rows) {
            if (!el.isJsonObject()) continue;
            JsonObject row = el.getAsJsonObject();
            if (!row.has("scheduleId") || !row.get("scheduleId").isJsonPrimitive()) continue;
            if (row.get("scheduleId").getAsInt() != scheduleId) continue;
            row.addProperty("disabled", disable);
            found = true;
            break;
        }

        if (!found) {
            ctx.status(400)
                    .json(
                            Map.of(
                                    "retcode",
                                    400,
                                    "message",
                                    "unknown scheduleId " + scheduleId + " in " + BANNERS_FILE));
            return;
        }

        if (!saveBannerTable(ctx, file, rows)) return;

        var result = new LinkedHashMap<String, Object>();
        result.put("retcode", 0);
        result.put("scheduleId", scheduleId);
        result.put("action", disable ? "disable" : "enable");
        // Report the state the caller can now verify by re-listing.
        var live = Grasscutter.getGameServer().getGachaSystem().getGachaBanners();
        result.put("loaded", live.containsKey(scheduleId));
        result.put("disabled", disable);

        ctx.contentType("application/json; charset=utf-8");
        ctx.json(result);
    }

    /**
     * Copies one row out of a historical revision and re-runs it in the live table.
     *
     * <p>The historical row's own window is useless for this: every old row carries the sentinel
     * {@code endTime = 1924992000} and a blank {@code beginTime}, so as-is it would either sit
     * dormant (begin in the past but end at the sentinel -- actually active, but indistinguishable
     * from the copy it replaced) or crowd out the banners already running. Instead the copy gets a
     * fresh configurable window starting now, which is long enough to be useful and short enough that
     * the re-run expires on its own if the operator forgets it.
     *
     * <p>If a row with the same scheduleId is already in the live table it is replaced in place, so a
     * re-run never leaves two rows for one schedule; otherwise the row is appended. {@code disabled}
     * is dropped, since re-running a banner that is still turned off would be a no-op the reload
     * quietly ignores.
     */
    private static void rerunBannerRow(Context ctx, int scheduleId, String commit, long now, long end)
            throws Exception {
        Path file = FileUtils.getDataPath(BANNERS_FILE);
        Path dataDir = file.getParent();

        JsonArray source;
        try {
            if (WORKING_REVISION.equals(commit)) {
                source = readBannerTable(file);
            } else if (ARCHIVE_REVISION.equals(commit)) {
                source = readArchiveBannerTable(file);
            } else {
                // Same repo-root path resolution as the history walk.
                source = readBannerTableFromGit(dataDir, commit, repoRelativeBannerPath(dataDir));
            }
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .warn("Could not read banner table for re-run (commit {}): {}", commit, e.getMessage());
            ctx.status(400)
                    .json(
                            Map.of(
                                    "retcode",
                                    400,
                                    "message",
                                    "could not read the banner table at " + commit + ": " + e.getMessage()));
            return;
        }

        JsonObject template = null;
        for (JsonElement el : source) {
            if (!el.isJsonObject()) continue;
            JsonObject row = el.getAsJsonObject();
            if (!row.has("scheduleId") || !row.get("scheduleId").isJsonPrimitive()) continue;
            if (row.get("scheduleId").getAsInt() == scheduleId) {
                template = row;
                break;
            }
        }
        if (template == null) {
            ctx.status(400)
                    .json(
                            Map.of(
                                    "retcode",
                                    400,
                                    "message",
                                    "scheduleId " + scheduleId + " is not in revision " + commit));
            return;
        }

        // Deep copy so the historical revision is never mutated, then stamp the fresh window.
        JsonObject row = JsonUtils.decode(JsonUtils.encode(template), JsonObject.class);
        row.addProperty("beginTime", now);
        row.addProperty("endTime", end);
        row.remove("disabled");

        JsonArray target;
        try {
            target = readBannerTable(file);
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .error("Could not read the live banner table at {}: {}", file, e.getMessage());
            ctx.status(500)
                    .json(
                            Map.of(
                                    "retcode",
                                    500,
                                    "message",
                                    "could not read " + BANNERS_FILE + ": " + e.getMessage()));
            return;
        }

        // Replace a same-scheduleId row in place; append when the schedule is new to the live table.
        boolean replaced = false;
        for (int i = 0; i < target.size(); i++) {
            JsonElement el = target.get(i);
            if (!el.isJsonObject()) continue;
            JsonObject existing = el.getAsJsonObject();
            if (!existing.has("scheduleId") || !existing.get("scheduleId").isJsonPrimitive()) continue;
            if (existing.get("scheduleId").getAsInt() == scheduleId) {
                target.set(i, row);
                replaced = true;
                break;
            }
        }
        if (!replaced) target.add(row);

        if (!saveBannerTable(ctx, file, target)) return;

        var live = Grasscutter.getGameServer().getGachaSystem().getGachaBanners();
        var result = new LinkedHashMap<String, Object>();
        result.put("retcode", 0);
        result.put("scheduleId", scheduleId);
        result.put("action", "rerun");
        result.put("from", commit);
        result.put("beginTime", now);
        result.put("endTime", end);
        result.put("loaded", live.containsKey(scheduleId));

        ctx.contentType("application/json; charset=utf-8");
        ctx.json(result);
    }

    /** Change only the deadline; keep the start, enabled flag, UP pool and pity configuration. */
    private static void rescheduleBannerRow(Context ctx, int scheduleId, JsonObject request)
            throws Exception {
        Path file = FileUtils.getDataPath(BANNERS_FILE);
        var rows = readBannerTable(file);
        for (var el : rows) {
            var row = el.getAsJsonObject();
            if (row.get("scheduleId").getAsInt() != scheduleId) continue;
            long end;
            try {
                end =
                        BannerConfig.requestedEnd(
                                request,
                                System.currentTimeMillis() / 1000L,
                                row.has("beginTime") ? row.get("beginTime").getAsLong() : 0);
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("retcode", 400, "message", e.getMessage()));
                return;
            }
            row.addProperty("endTime", end);
            if (!saveBannerTable(ctx, file, rows)) return;
            ctx.json(
                    Map.of("retcode", 0, "scheduleId", scheduleId, "action", "reschedule", "endTime", end));
            return;
        }
        ctx.status(400).json(Map.of("retcode", 400, "message", "该卡池已不在当前配置中，请刷新后重试。"));
    }

    /**
     * Writes the banner table atomically and reloads the gacha system, answering the failure itself
     * and returning false so the caller knows not to answer on top of it.
     */
    private static boolean saveBannerTable(Context ctx, Path file, JsonArray rows) throws Exception {
        // Write to a sibling temp file and move it into place, so a failure partway through leaves
        // the original table intact rather than half-overwritten.
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(temp, JsonUtils.encode(rows), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .error("Could not write the banner table at {}: {}", file, e.getMessage());
            try {
                Files.deleteIfExists(temp);
            } catch (Exception ignored) {
            }
            ctx.status(500)
                    .json(
                            Map.of(
                                    "retcode",
                                    500,
                                    "message",
                                    "could not write " + BANNERS_FILE + ": " + e.getMessage()));
            return false;
        }

        // The working tree is revision zero of the history walk, so it has just gone stale.
        bannerHistoryCache = null;

        // The config leaves the file watcher off, so the reload is ours to trigger. Without it the
        // change would only take effect at the next startup.
        try {
            if (!Grasscutter.getGameServer().getGachaSystem().load())
                throw new IllegalStateException("卡池重载失败，仍使用之前的配置。");
        } catch (Exception e) {
            Grasscutter.getLogger().error("Banner table saved, but the reload failed.", e);
            ctx.status(500)
                    .json(
                            Map.of(
                                    "retcode",
                                    500,
                                    "message",
                                    "saved, but the gacha reload failed: " + e.getMessage()));
            return false;
        }
        return true;
    }

    /** Prefer editable JSON; a fresh runtime can still use the bundled TSJ defaults. */
    private static JsonArray readBannerTable(Path file) throws Exception {
        return BannerConfig.normalize(BannerConfig.read(file));
    }

    /**
     * The banner table as recorded in a git revision of {@code Banners.json}.
     *
     * <p>{@code git show <rev>:<path>} reads the path relative to the repository root, not the
     * process working directory the way a pathspec after {@code --} does, so the path has to be
     * resolved against the root first.
     */
    private static JsonArray readBannerTableFromGit(Path dataDir, String commit, String rootPath)
            throws Exception {
        String raw = git(dataDir, "show", commit + ":" + rootPath);
        return BannerConfig.normalize(JsonUtils.decode(raw, JsonArray.class));
    }

    /** {@code data/Banners.json} expressed the way {@code git show <rev>:<path>} wants it. */
    private static String repoRelativeBannerPath(Path dataDir) throws Exception {
        Path repoRoot = Path.of(git(dataDir, "rev-parse", "--show-toplevel").trim());
        // Git wants forward slashes on every platform; Windows gives backslashes.
        return repoRoot
                .relativize(dataDir.toAbsolutePath())
                .resolve(BANNERS_FILE)
                .toString()
                .replace('\\', '/');
    }

    /** Reads the banner table as mapped objects, for the list endpoint's status columns. */
    private static List<GachaBanner> loadBannerRows() {
        try {
            return java.util.stream.StreamSupport.stream(
                            readBannerTable(FileUtils.getDataPath(BANNERS_FILE)).spliterator(), false)
                    .map(row -> JsonUtils.decode(row, GachaBanner.class))
                    .toList();
        } catch (Exception e) {
            Grasscutter.getLogger().warn("Could not load the banner table: {}", e.getMessage());
        }
        return List.of();
    }

    // ------------------------------------------------------------------
    // Banner history
    // ------------------------------------------------------------------

    /**
     * One revision of the banner table, as the history view renders it.
     *
     * <p>{@code archive} marks the pseudo-revisions built from the disabled archive rows living in
     * the working copy: they share {@link #WORKING_REVISION} as their commit (so re-running one
     * resolves the same way as re-running a working-copy row) but are not the working copy itself.
     */
    record HistoryTable(
            String commit, String date, String subject, List<GachaBanner> banners, boolean archive) {}

    /**
     * The parsed revisions, minus the live state. {@link #listBannerHistory} stamps {@code loaded}
     * and {@code active} per request, so a cache built before the operator flipped a banner never
     * serves a stale answer about what is running now. Set to null by any failure, so the next
     * request re-walks git instead of caching an empty history.
     */
    private static volatile List<HistoryTable> bannerHistoryCache;

    /**
     * Lists every banner table the repository has ever recorded, newest first.
     *
     * <p>Each revision carries the banners it defined plus the version and the approximate official
     * opening month, so the page can show which banners ran in which order on the official server --
     * the file itself records no windows, only the disabled flag, so without this walk there is no
     * way to answer "what was running before this".
     *
     * <p>The working-tree copy comes first, as the revision the operator is currently editing;
     * revisions behind that are read out of git. Identical tables are collapsed: the repo commits
     * sometimes pick up a file unchanged, and two entries showing the same four banners add nothing.
     */
    private static void listBannerHistory(Context ctx) throws Exception {
        if (!authorize(ctx)) return;

        var live = Grasscutter.getGameServer().getGachaSystem().getGachaBanners();
        long now = System.currentTimeMillis() / 1000L;
        var strings = Language.getTextMapStrings();

        List<HistoryTable> tables = bannerHistoryCache;
        if (tables == null) {
            try {
                tables = loadBannerHistory();
                bannerHistoryCache = tables;
            } catch (Exception e) {
                Grasscutter.getLogger().warn("Could not walk the banner history: {}", e.getMessage());
                // Answer with just the working copy, and leave the cache null so the next request
                // retries git instead of caching the degraded answer.
                tables = List.of(new HistoryTable(WORKING_REVISION, "", "当前工作区", loadBannerRows(), false));
            }
        }

        var revisions = new ArrayList<Map<String, Object>>();
        for (var table : tables) {
            var banners = new ArrayList<Map<String, Object>>();
            for (GachaBanner banner : table.banners()) {
                banners.add(bannerRowOf(strings, banner, live, now));
            }
            // Sort each revision into the order the banners ran in on the official server: the
            // version's slot first, then the schedule id as a tiebreak.
            banners.sort(
                    (a, b2) -> {
                        int byPhase =
                                Integer.compare(
                                        phaseOrder((int) a.get("gachaType")), phaseOrder((int) b2.get("gachaType")));
                        return byPhase != 0
                                ? byPhase
                                : Integer.compare((int) a.get("scheduleId"), (int) b2.get("scheduleId"));
                    });

            var revision = new LinkedHashMap<String, Object>();
            revision.put("commit", table.commit());
            revision.put("date", table.date());
            revision.put("subject", table.subject());
            revision.put("version", versionOf(table.subject()));
            revision.put("officialStart", officialStartLabel(table.subject()));
            revision.put("working", WORKING_REVISION.equals(table.commit()));
            revision.put("archive", table.archive());
            revision.put("bannerCount", banners.size());
            revision.put("banners", banners);
            revisions.add(revision);
        }

        // Keep the current configuration on top, then sort archives and commits by version.
        revisions.sort(
                (a, b) -> {
                    boolean aWorking =
                            Boolean.TRUE.equals(a.get("working")) && !Boolean.TRUE.equals(a.get("archive"));
                    boolean bWorking =
                            Boolean.TRUE.equals(b.get("working")) && !Boolean.TRUE.equals(b.get("archive"));
                    if (aWorking != bWorking) return aWorking ? -1 : 1;
                    int byVersion =
                            Integer.compare(
                                    officialStartOrder((String) b.get("version")),
                                    officialStartOrder((String) a.get("version")));
                    if (byVersion != 0) return byVersion;
                    return ((String) b.get("date")).compareTo((String) a.get("date"));
                });

        ctx.contentType("application/json; charset=utf-8");
        ctx.json(Map.of("retcode", 0, "revisions", revisions));
    }

    /**
     * Walks git for the banner table's history. The working tree is revision zero; each commit that
     * touched {@code Banners.json} after that contributes the table as it stood in that commit.
     */
    private static List<HistoryTable> loadBannerHistory() throws Exception {
        return loadBannerHistory(FileUtils.getDataPath(BANNERS_FILE));
    }

    static List<HistoryTable> loadBannerHistory(Path file) throws Exception {
        Path dataDir = file.getParent();

        var tables = new ArrayList<HistoryTable>();
        var seen = new HashSet<String>();

        // Separate live rows from disabled archive templates. The archive version lives in the
        // comment string, which GachaBanner does not keep.
        var workingRows = new ArrayList<GachaBanner>();
        var archiveByVersion = new TreeMap<String, List<GachaBanner>>(GmHandler::compareVersions);
        for (JsonElement el : readBannerTable(file)) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            GachaBanner banner;
            try {
                banner = JsonUtils.decode(el, GachaBanner.class);
            } catch (Exception e) {
                continue; // A row this build cannot map is left out, same as for a git revision.
            }
            if (banner == null) continue;
            if (!isArchiveRow(obj, banner)) workingRows.add(banner);
        }
        // The bundled catalogue also works for existing Docker volumes with an older live table.
        // Reading it never changes that table or the currently enabled banners.
        for (JsonElement el : readArchiveBannerTable(file)) {
            JsonObject obj = el.getAsJsonObject();
            var banner = JsonUtils.decode(el, GachaBanner.class);
            archiveByVersion
                    .computeIfAbsent(archiveRowVersion(obj, banner), k -> new ArrayList<>())
                    .add(banner);
        }

        // The table the operator is looking at right now, so a live edit sits at the top of the list
        // rather than being hidden behind the last commit.
        addHistoryRevision(tables, seen, WORKING_REVISION, "", "当前工作区", workingRows, false);

        // Each version becomes its own entry in the history list, newest first, so the page can show
        // "this is what version 2.4 ran" without the operator having to scroll one giant table.
        for (var entry : archiveByVersion.descendingMap().entrySet()) {
            addHistoryRevision(
                    tables,
                    seen,
                    ARCHIVE_REVISION,
                    "",
                    "官服归档 · Version " + entry.getKey(),
                    entry.getValue(),
                    true);
        }

        // %x00 separates fields, %cs is the commit date as YYYY-MM-DD. -z keeps each commit's
        // record on one line, so one read of the stream is the whole log.
        // -z NUL-terminates each commit's record, and %x1f separates the fields inside one, so a
        // split on NUL yields whole records instead of the individual fields git wrote between them.
        String rootPath;
        String log;
        try {
            rootPath = repoRelativeBannerPath(dataDir);
            log = git(dataDir, "log", "-z", "--format=%H%x1f%cs%x1f%s", "--", BANNERS_FILE);
        } catch (Exception e) {
            // Git is optional in a runtime image. Keep the current table and bundled archive.
            Grasscutter.getLogger().debug("Banner commit history is unavailable: {}", e.getMessage());
            return tables;
        }
        for (String record : log.split("\u0000")) {
            if (record.isBlank()) continue;
            String[] parts = record.split("\u001f", 3);
            if (parts.length < 3) continue;
            String sha = parts[0];
            String date = parts[1];
            String subject = parts[2];
            try {
                addHistoryRevision(
                        tables,
                        seen,
                        sha.substring(0, 7),
                        date,
                        subject,
                        readBannerTableFromGit(dataDir, sha, rootPath));
            } catch (Exception e) {
                // A commit whose table no longer parses is skipped rather than failing the walk;
                // the rows this build does not model are still listed in the live view.
                Grasscutter.getLogger().debug("Skipping banner table at {}: {}", sha, e.getMessage());
            }
        }

        return tables;
    }

    /** Local archive edits override bundled templates without changing enabled live rows. */
    static JsonArray readArchiveBannerTable(Path file) throws Exception {
        var bundled =
                JsonUtils.decode(
                        new String(FileUtils.readResource(BANNER_ARCHIVE_RESOURCE), StandardCharsets.UTF_8),
                        JsonArray.class);
        var rows = new LinkedHashMap<Integer, JsonObject>();
        for (var table : List.of(bundled, readBannerTable(file))) {
            for (var el : table) {
                if (!el.isJsonObject()) continue;
                var obj = el.getAsJsonObject();
                var banner = JsonUtils.decode(el, GachaBanner.class);
                if (banner != null && isArchiveRow(obj, banner)) rows.put(banner.getScheduleId(), obj);
            }
        }
        var result = new JsonArray();
        rows.values().forEach(result::add);
        return result;
    }

    /**
     * Parses a raw table and appends it, unless an earlier revision already had the identical rows.
     */
    private static void addHistoryRevision(
            List<HistoryTable> tables,
            Set<String> seen,
            String commit,
            String date,
            String subject,
            JsonArray raw) {
        var banners = new ArrayList<GachaBanner>();
        for (JsonElement el : raw) {
            if (!el.isJsonObject()) continue;
            try {
                GachaBanner banner = JsonUtils.decode(el, GachaBanner.class);
                if (banner != null) banners.add(banner);
            } catch (Exception ignored) {
                // A row this build cannot map is left out of the history list, but does not stop it.
            }
        }
        addHistoryRevision(tables, seen, commit, date, subject, banners, false);
    }

    /**
     * Appends a decoded table, unless an earlier revision already had the identical rows. Archive
     * revisions use {@code ARCHIVE_REVISION} so re-runs resolve the bundled and local templates
     * without requiring Git in the runtime image.
     */
    private static void addHistoryRevision(
            List<HistoryTable> tables,
            Set<String> seen,
            String commit,
            String date,
            String subject,
            List<GachaBanner> banners,
            boolean archive) {
        // Dedupe on the rendered rows: the fingerprint is the schedule ids and UP items, which is
        // what the operator is comparing between revisions anyway.
        String fingerprint = bannerFingerprint(banners);
        if (!seen.add(fingerprint)) return;

        tables.add(new HistoryTable(commit, date, subject, List.copyOf(banners), archive));
    }

    /**
     * True when a row is a disabled official-archive entry rather than a live banner. The schedule id
     * is the marker: archive ids are encoded as {@code 90000 + (major*100+minor)*100 + index}, while
     * every live id the game has shipped stays far below that.
     */
    /**
     * True when a row is still a disabled archive entry. The schedule id marks the archive range, and
     * the disabled flag separates those from a row the operator has re-run out of the archive: a
     * re-run keeps its schedule id but drops the flag, and the gacha system then loads it, so the
     * live list has to show it.
     */
    private static boolean isArchivedBanner(GachaBanner banner) {
        return banner.getScheduleId() >= ARCHIVE_SCHEDULE_ID_BASE && banner.isDisabled();
    }

    private static boolean isArchiveRow(JsonObject obj, GachaBanner banner) {
        if (banner.getScheduleId() < ARCHIVE_SCHEDULE_ID_BASE) return false;
        return Boolean.TRUE.equals(
                obj.has("disabled") && obj.get("disabled").isJsonPrimitive()
                        ? obj.get("disabled").getAsBoolean()
                        : banner.isDisabled());
    }

    /**
     * The version an archive row belongs to. The comment names it directly; the schedule id carries
     * the same encoding as a fallback for rows edited by hand.
     */
    private static String archiveRowVersion(JsonObject obj, GachaBanner banner) {
        if (obj.has("comment") && obj.get("comment").isJsonPrimitive()) {
            var m = ARCHIVE_VERSION_IN_COMMENT.matcher(obj.get("comment").getAsString());
            if (m.find()) return m.group(1);
        }
        int packed = (banner.getScheduleId() - ARCHIVE_SCHEDULE_ID_BASE) / 100;
        return (packed / 100) + "." + (packed % 100);
    }

    /** Orders version labels numerically, so "7.0" sorts after "6.7" and "10.0" after "9.9". */
    private static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int va = i < pa.length ? Integer.parseInt(pa[i]) : 0;
            int vb = i < pb.length ? Integer.parseInt(pb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    /** A short identity for a table, so a commit that carried the file unchanged appears once. */
    private static String bannerFingerprint(List<GachaBanner> banners) {
        var sb = new StringBuilder();
        for (GachaBanner b : banners) {
            sb.append(b.getScheduleId()).append(':').append(effectiveGachaType(b)).append(':');
            sb.append(b.getRateUpItems5() == null ? "" : Arrays.toString(b.getRateUpItems5()));
            sb.append(b.getRateUpItems4() == null ? "" : Arrays.toString(b.getRateUpItems4()));
            sb.append('|');
        }
        return sb.toString();
    }

    /** The version a commit's subject names, or "" when it names none. */
    private static String versionOf(String subject) {
        if (subject == null || subject.isBlank()) return "";
        var m = VERSION_IN_SUBJECT.matcher(subject);
        return m.find() ? m.group(1) : "";
    }

    /**
     * The official opening month for the version a subject names, or "" when it is not in the table.
     */
    private static String officialStartLabel(String subject) {
        YearMonth ym = OFFICIAL_VERSION_START.get(versionOf(subject));
        return ym != null ? ym.toString() : "";
    }

    /** The official opening month as a sort key, unknowns last. */
    private static int officialStartOrder(String version) {
        YearMonth ym = OFFICIAL_VERSION_START.getOrDefault(version, YearMonth.of(1900, 1));
        return (ym.getYear() - 1900) * 12 + ym.getMonthValue();
    }

    /**
     * Runs git in the data directory. Throws when git is missing or exits non-zero, so callers can
     * degrade to the working copy with one catch.
     */
    private static String git(Path dir, String... args) throws Exception {
        var cmd = new ArrayList<String>(args.length + 1);
        cmd.add("git");
        cmd.addAll(Arrays.asList(args));
        var pb = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(false);
        var proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String err = new String(proc.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = proc.waitFor();
        if (code != 0) {
            throw new IllegalStateException("git exited " + code + ": " + err.trim());
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Authorization
    // ------------------------------------------------------------------

    /**
     * Checks the request against the configured token and loopback setting. Returns false (and
     * answers 403) when the request is not allowed, so a route just writes {@code if
     * (!authorize(ctx)) return;} before it does anything.
     *
     * <p>The token is accepted as a Bearer header -- what the page's own fetch calls send -- and as a
     * {@code token} query parameter, which is what a bookmarked URL carries. The console page stores
     * it in localStorage, so neither the server nor the page ever sees a player's session
     * credentials.
     *
     * <p>An empty configured token means the console is unauthenticated: any loopback request is let
     * in. That is how this repo's config ships it, so an operator just opens the page and is never
     * asked for a secret. {@code loopbackOnly} above is then the only thing standing between the
     * console and the LAN.
     */
    private static boolean authorize(Context ctx) throws Exception {
        if (SERVER.gm.loopbackOnly) {
            try {
                if (!InetAddress.getByName(ctx.ip()).isLoopbackAddress()) {
                    ctx.status(403).result("403: the GM console is loopback-only on this server.");
                    return false;
                }
            } catch (Exception ignored) {
                // An address that cannot be parsed is not one that can be trusted either.
                ctx.status(403).result("403: the GM console cannot verify where this request came from.");
                return false;
            }
        }

        String expected = SERVER.gm.accessToken;
        if (expected == null || expected.isEmpty()) {
            // No token configured: the console is open. See the javadoc for why this is safe.
            return true;
        }

        String presented = tokenOf(ctx);
        if (presented == null || !presented.equals(expected)) {
            ctx.status(403).result("403: the GM console requires a valid access token.");
            return false;
        }
        return true;
    }

    /** Bearer header first, then the {@code token} query parameter. */
    private static String tokenOf(Context ctx) {
        String header = ctx.header("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        return ctx.queryParam("token");
    }

    // ------------------------------------------------------------------
    // Output capture
    // ------------------------------------------------------------------

    /**
     * Captures what a command prints, which is what makes the page useful: without it, a command that
     * fails tells the browser nothing.
     *
     * <p>Every line a command emits goes through {@link
     * emu.grasscutter.command.CommandHandler#sendMessage}, which fires a {@link
     * ReceiveCommandFeedbackEvent}. There is no unregister on {@link EventHandler}, so one listener
     * is registered once and serves every request; per-request isolation comes from the buffer being
     * held in an inheritable thread-local, which the threads that {@code threading} commands spawn
     * inherit rather than share.
     */
    private static final class CommandCapture {
        private static final InheritableThreadLocal<ConcurrentLinkedQueue<String>> BUFFER =
                new InheritableThreadLocal<>();

        /** Registered once; the buffer it writes to is whatever the running thread armed. */
        private static volatile boolean listenerRegistered;

        /** How long to keep waiting for output after invoke returns, in total. */
        private static final long SETTLE_LIMIT_MS = 2000;

        /** How long to wait between checks that the output has stopped moving. */
        private static final long SETTLE_STEP_MS = 100;

        static void registerListener() {
            if (listenerRegistered) return;
            synchronized (CommandCapture.class) {
                if (listenerRegistered) return;
                new EventHandler<>(ReceiveCommandFeedbackEvent.class)
                        .listener(
                                event -> {
                                    var buffer = BUFFER.get();
                                    if (buffer != null && event.getMessage() != null) {
                                        buffer.add(event.getMessage());
                                    }
                                })
                        .register();
                listenerRegistered = true;
            }
        }

        /** Runs {@code command} as the console and returns every line it printed. */
        static List<String> invokeAndCapture(String command) {
            registerListener();

            var buffer = new ConcurrentLinkedQueue<String>();
            BUFFER.set(buffer);
            try {
                // A null sender is the console, which the permission handler treats as privileged;
                // the @uid inside the message is what assigns the target.
                CommandMap.getInstance().invoke(null, null, command);

                // Wait until the output stops arriving. A threaded command is still running on its
                // own thread when invoke returns, so this is the only window its lines have.
                long deadline = System.currentTimeMillis() + SETTLE_LIMIT_MS;
                int seen = 0;
                while (System.currentTimeMillis() < deadline) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(SETTLE_STEP_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    if (buffer.size() == seen) break; // Output settled.
                    seen = buffer.size();
                }
                return new ArrayList<>(buffer);
            } finally {
                BUFFER.remove();
            }
        }
    }
}
