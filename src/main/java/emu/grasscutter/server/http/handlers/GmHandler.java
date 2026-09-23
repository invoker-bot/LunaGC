package emu.grasscutter.server.http.handlers;

import static emu.grasscutter.config.Configuration.SERVER;

import com.google.gson.JsonObject;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandMap;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.event.EventHandler;
import emu.grasscutter.server.event.game.ReceiveCommandFeedbackEvent;
import emu.grasscutter.server.http.Router;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.JsonUtils;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

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
 * <p>Routes are guarded by {@link
 * emu.grasscutter.config.ConfigContainer.Server.GM#accessToken the configured token}; a request
 * without it gets a 403 and nothing else, and an unset token disables the console completely. The
 * console page itself is a single self-contained HTML file at {@code /gm/console.html} in resources,
 * so the server ships it and the browser runs it.
 */
public final class GmHandler implements Router {
    private static final String CONSOLE_RESOURCE = "/gm/console.html";

    @Override
    public void applyRoutes(Javalin javalin) {
        javalin.get("/gm", GmHandler::serveConsole);
        javalin.get("/gm/", GmHandler::serveConsole);
        javalin.get("/gm/api/commands", GmHandler::listCommands);
        javalin.get("/gm/api/players", GmHandler::listPlayers);
        javalin.post("/gm/api/command", GmHandler::runCommand);
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
     * annotated {@code threading} ({@link CommandMap} spawns it on a bare thread), so the response
     * is settled rather than waited on: after invoke returns, the capture keeps draining until the
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
    // Authorization
    // ------------------------------------------------------------------

    /**
     * Checks the request against the configured token and loopback setting. Returns false (and
     * answers 403) when the request is not allowed, so a route just writes
     * {@code if (!authorize(ctx)) return;} before it does anything.
     *
     * <p>The token is accepted as a Bearer header -- what the page's own fetch calls send -- and as
     * a {@code token} query parameter, which is what a bookmarked URL carries. The console page
     * stores it in localStorage, so neither the server nor the page ever sees a player's session
     * credentials.
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
                ctx.status(403)
                        .result("403: the GM console cannot verify where this request came from.");
                return false;
            }
        }

        String expected = SERVER.gm.accessToken;
        if (expected == null || expected.isEmpty()) {
            ctx.status(403)
                    .result(
                            "403: the GM console is disabled. Set server.gm.accessToken in config.json and"
                                    + " restart.");
            return false;
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
     * Captures what a command prints, which is what makes the page useful: without it, a command
     * that fails tells the browser nothing.
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
