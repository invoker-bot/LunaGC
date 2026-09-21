package emu.grasscutter.server.dev;

import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.PacketOpcodesUtils;
import emu.grasscutter.server.event.EventHandler;
import emu.grasscutter.server.event.HandlerPriority;
import emu.grasscutter.server.event.game.UnimplementedRequestEvent;
import emu.grasscutter.server.game.GameSession;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The developer-mode check.
 *
 * <p>Registered by {@link Grasscutter#main(String[])} only when the server was started with
 * {@code -dev}. It listens on {@link UnimplementedRequestEvent} and files every request the server
 * decided it probably does not implement into {@code debug/dev-report/unimplemented.md}, one
 * section per request, with the player, the scene, the opcode and the payload's fields attached.
 *
 * <p>The point is that a feature which is only half there is indistinguishable from a feature that
 * works until somebody uses the part that is missing. The artifact upgrade screen's null-exception
 * dialog was exactly this for a long time: {@code upgradeRelic} has three early returns and only
 * the happy path answers the client, so a request the server rejected looked, from the client's
 * side, like a request the server ignored. This reporter is what names the next one of those.
 *
 * <p>LOW priority so a plugin that actually implements the packet gets to run first and cancel the
 * event; the reporter only files what nobody claimed.
 */
public final class UnimplementedRequestReporter {

    /** Where the report is written. Relative to the working directory the server was started in. */
    private static final Path REPORT =
            Path.of("debug", "dev-report", "unimplemented.md");

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** (reason, opcode) pairs already filed, so the report names each gap once per session. */
    private static final Set<Long> filed = ConcurrentHashMap.newKeySet();

    private UnimplementedRequestReporter() {}

    /** Hooks the reporter onto the event bus. No-op when developer mode is off. */
    public static void register() {
        if (!GameConstants.DEVELOPER_MODE) return;

        new EventHandler<>(UnimplementedRequestEvent.class)
                .priority(HandlerPriority.LOW)
                .ignore(false)
                .listener(UnimplementedRequestReporter::onUnimplemented)
                .register();
    }

    private static void onUnimplemented(UnimplementedRequestEvent event) {
        // A handler pair is worth exactly one section; a player hammering the same broken button
        // would otherwise fill the file with it.
        if (!filed.add(key(event.getReason(), event.getOpcode()))) return;

        var name = PacketOpcodesUtils.getOpcodeName(event.getOpcode());
        var player = event.getPlayer();

        Grasscutter.getLogger()
                .warn(
                        "[dev] {} ({}) is probably unimplemented - {} - {}",
                        name,
                        event.getOpcode(),
                        event.getReason().getDescription(),
                        describe(player));

        writeSection(event, name, player);
    }

    private static String describe(Player player) {
        if (player == null) return "an unlogged session";
        var scene = player.getScene();
        return "uid " + player.getUid()
                + " '" + player.getNickname() + "'"
                + (scene != null
                        ? " in scene " + scene.getId() + " at " + player.getPosition()
                        : " in no scene");
    }

    private static void writeSection(UnimplementedRequestEvent event, String name, Player player) {
        var sb = new StringBuilder();
        sb.append("\n## ").append(name).append(" (").append(event.getOpcode()).append(")\n\n");
        sb.append("- when      : ")
                .append(LocalDateTime.now().format(TIMESTAMP))
                .append('\n');
        sb.append("- verdict   : ").append(event.getReason()).append(" - ")
                .append(event.getReason().getDescription())
                .append('\n');
        sb.append("- detail    : ").append(event.getDetail()).append('\n');
        sb.append("- player    : ").append(describe(player)).append('\n');
        sb.append("- payload   : ").append(event.getPayload().length).append(" bytes\n");
        sb.append("- fields    : ").append(event.getFields()).append('\n');
        sb.append("- next step : ");
        switch (event.getReason()) {
            case NO_HANDLER ->
                    sb.append("no handler class exists for this opcode. Add one under ")
                            .append("server/packet/recv with @Opcodes(PacketOpcodes.")
                            .append(name)
                            .append("); the fields above are the proto to write.\n");
            case HANDLER_THREW ->
                    sb.append("the handler raised. The full stack trace is in the server log ")
                            .append("just before this entry; fix it or guard the input.\n");
            case NO_RESPONSE ->
                    sb.append("the handler ran and sent nothing. Find it in ")
                            .append("server/packet/recv and make every early return send a Rsp ")
                            .append("with an error retcode -- a request the server rejects is ")
                            .append("still a request the client is waiting on.\n");
        }
        sb.append('\n');

        try {
            Files.createDirectories(REPORT.getParent());
            // Created if missing, appended to otherwise: a report from a previous session is
            // evidence, not clutter, and the header below is what separates the two.
            if (!Files.exists(REPORT)) {
                Files.writeString(
                        REPORT,
                        "# Unimplemented requests\n\n"
                                + "Requests the server decided it probably does not implement, "
                                + "collected by developer mode.\n"
                                + "Each entry is one (reason, opcode) pair per session; the field "
                                + "numbers identify the message\n"
                                + "and the wire types plus values identify each field.\n",
                        StandardCharsets.UTF_8);
            }
            Files.write(
                    REPORT,
                    sb.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.APPEND);
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .warn("[dev] could not write the unimplemented-request report", e);
        }
    }

    /** One key per (reason, opcode), so the same opcode can be filed for two different reasons. */
    private static long key(UnimplementedRequestEvent.Reason reason, int opcode) {
        return ((long) reason.ordinal()) << 32 | (opcode & 0xFFFFFFFFL);
    }
}
