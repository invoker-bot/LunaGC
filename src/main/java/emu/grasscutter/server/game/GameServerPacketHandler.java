package emu.grasscutter.server.game;

import static emu.grasscutter.config.Configuration.GAME_INFO;
import static emu.grasscutter.config.Configuration.SERVER;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerDebugMode;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.event.game.ReceivePacketEvent;
import emu.grasscutter.server.game.GameSession.SessionState;
import it.unimi.dsi.fastutil.ints.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
public final class GameServerPacketHandler {

    private final Int2ObjectMap<PacketHandler> handlers;

    public GameServerPacketHandler(Class<? extends PacketHandler> handlerClass) {
        this.handlers = new Int2ObjectOpenHashMap<>();

        this.registerHandlers(handlerClass);
    }

    public void registerPacketHandler(Class<? extends PacketHandler> handlerClass) {
        try {
            var opcode = handlerClass.getAnnotation(Opcodes.class);
            if (opcode == null || opcode.disabled() || opcode.value() <= 0) {
                return;
            }

            var packetHandler = handlerClass.getDeclaredConstructor().newInstance();
            this.handlers.put(opcode.value(), packetHandler);
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .warn("Unable to register handler {}.", handlerClass.getSimpleName(), e);
        }
    }

    public void registerHandlers(Class<? extends PacketHandler> handlerClass) {
        var handlerClasses = Grasscutter.reflector.getSubTypesOf(handlerClass);
        for (var obj : handlerClasses) {
            this.registerPacketHandler(obj);
        }

        Grasscutter.getLogger()
                .debug("Registered " + this.handlers.size() + " " + handlerClass.getSimpleName() + "s");
    }

    public void handle(GameSession session, int opcode, byte[] header, byte[] payload) {
        PacketHandler handler = this.handlers.get(opcode);

        // 7.0 renumbered every message's fields (DropItemReq moved guid 15 -> 5 and pos 1 -> 12), so a
        // Req with no generated proto can only be rebuilt from what the client actually puts on the
        // wire. The field numbers name the message; the wire types and values name each field. Only
        // unhandled opcodes are announced, once each, so walking through a feature lists exactly what
        // it needs without flooding the log.
        if (handler == null && unannounced.add(opcode)) {
            var fields = describeFields(payload);
            Grasscutter.getLogger()
                    .info(
                            "{} ({}) arrived and nothing handles it - {} bytes - fields {}",
                            PacketOpcodesUtils.getOpcodeName(opcode),
                            opcode,
                            payload.length,
                            fields);
            // This event is what developer mode turns into a written report; see
            // UnimplementedRequestReporter. It rides the same once-per-opcode guard as the log line.
            new emu.grasscutter.server.event.game.UnimplementedRequestEvent(
                            session, opcode, payload, fields,
                            emu.grasscutter.server.event.game.UnimplementedRequestEvent.Reason.NO_HANDLER,
                            "no PacketHandler is registered for this opcode")
                    .call();
        }

        if (handler != null) {
            try {

                SessionState state = session.getState();

                if (opcode == PacketOpcodes.PingReq) {

                } else if (opcode == PacketOpcodes.GetPlayerTokenReq) {
                    if (state != SessionState.WAITING_FOR_TOKEN) {
                        return;
                    }
                } else if (state == SessionState.ACCOUNT_BANNED) {
                    session.close();
                    return;
                } else if (opcode == PacketOpcodes.PlayerLoginReq) {
                    if (state != SessionState.WAITING_FOR_LOGIN) {
                        return;
                    }
                } else if (opcode == PacketOpcodes.SetPlayerBornDataReq) {
                    if (state != SessionState.PICKING_CHARACTER) {
                        return;
                    }
                } else {
                    if (state != SessionState.ACTIVE) {
                        return;
                    }
                }

                ReceivePacketEvent event = new ReceivePacketEvent(session, opcode, payload);
                event.call();
                if (!event.isCanceled()) {
                    // Counted around the handler rather than inside it: a Req handler answers by
                    // delegating to a manager, so the only reliable way to see 'nothing went back' is
                    // to count what left the session. Only in developer mode - otherwise this is a
                    // field read per packet spent on nothing.
                    long sentBefore = emu.grasscutter.GameConstants.DEVELOPER_MODE ? session.getPacketsSent() : 0L;
                    handler.handle(session, header, event.getPacketData());
                    if (emu.grasscutter.GameConstants.DEVELOPER_MODE
                            && session.getPacketsSent() == sentBefore
                            && PacketOpcodesUtils.getOpcodeName(opcode).endsWith("Req")) {
                        new emu.grasscutter.server.event.game.UnimplementedRequestEvent(
                                        session, opcode, payload, describeFields(payload),
                                        emu.grasscutter.server.event.game.UnimplementedRequestEvent.Reason
                                                .NO_RESPONSE,
                                        handler.getClass().getName() + " handled the request and sent nothing")
                                .call();
                    }
                }
            } catch (Throwable ex) {
                // Printed to the console it never reached the log file, so an action that quietly did
                // nothing left nothing behind to explain it. Throwable rather than Exception because
                // one malformed packet should not be able to take a player's connection with it.
                Grasscutter.getLogger()
                        .error(
                                "{} threw while handling {} for {}.",
                                handler.getClass().getSimpleName(),
                                PacketOpcodesUtils.getOpcodeName(opcode),
                                session.getPlayer() != null ? session.getPlayer().getUid() : "an unlogged session",
                                ex);
                new emu.grasscutter.server.event.game.UnimplementedRequestEvent(
                                session, opcode, payload, describeFields(payload),
                                emu.grasscutter.server.event.game.UnimplementedRequestEvent.Reason.HANDLER_THREW,
                                handler.getClass().getName() + " raised: " + ex)
                        .call();
            }
            return;
        }

        // Nothing answers this one, so the player's action does nothing - already reported above,
        // along with the fields, which is how the CmdId of an unimplemented feature is discovered:
        // go and use the feature in game, and the client names the packet it wanted.
    }

    /** Opcodes already reported as unhandled, so the log says it once rather than every packet. */
    private final Set<Integer> unannounced = ConcurrentHashMap.newKeySet();

    // Beyond this the dump stops recursing into embedded messages; a client can nest arbitrarily deep,
    // and the outer two or three layers are all a handler ever needs to read.
    private static final int MAX_NESTING = 3;
    private static final int MAX_VALUES_PER_FIELD = 4;
    private static final int MAX_HEX_BYTES = 16;

    /**
     * Renders an unknown payload as {@code {no:wire=value, ...}}. The field numbers identify the
     * message, and the wire types plus values identify the fields, which is enough to hand-write a
     * proto for a feature the client is already using.
     */
    private static String describeFields(byte[] payload) {
        try {
            return describeFields(com.google.protobuf.UnknownFieldSet.parseFrom(payload), 0);
        } catch (Exception e) {
            return "<unparsed>";
        }
    }

    private static String describeFields(com.google.protobuf.UnknownFieldSet fields, int depth) {
        var map = fields.asMap();
        if (map.isEmpty()) {
            return "{}";
        }
        var sb = new StringBuilder("{");
        boolean firstField = true;
        for (int number : new java.util.TreeSet<>(map.keySet())) {
            var field = map.get(number);
            var values = new java.util.ArrayList<String>();
            appendVarints(values, field.getVarintList(), "varint");
            appendFixed(values, field.getFixed32List(), "fixed32");
            appendFixed(values, field.getFixed64List(), "fixed64");
            for (int i = 0;
                    i < Math.min(field.getLengthDelimitedList().size(), MAX_VALUES_PER_FIELD);
                    i++) {
                values.add(describeBlob(field.getLengthDelimitedList().get(i), depth));
            }
            if (values.isEmpty()) {
                continue;
            }
            if (!firstField) {
                sb.append(", ");
            }
            firstField = false;
            sb.append(number).append(':').append(String.join("|", values));
        }
        return sb.append('}').toString();
    }

    /** A length-delimited field is either an embedded message or raw bytes; parsing the whole blob
     * cleanly is the cheapest discriminator, and recursion lays its own fields out the same way. */
    private static String describeBlob(com.google.protobuf.ByteString blob, int depth) {
        if (depth < MAX_NESTING) {
            try {
                var nested = com.google.protobuf.UnknownFieldSet.parseFrom(blob);
                if (!nested.asMap().isEmpty()) {
                    return "msg[" + blob.size() + "]=" + describeFields(nested, depth + 1);
                }
            } catch (Exception ignored) {
                // Not a message: it is plain bytes, described below.
            }
        }
        var bytes = blob.toByteArray();
        var sb = new StringBuilder("len[").append(bytes.length).append("]=");
        int shown = Math.min(bytes.length, MAX_HEX_BYTES);
        for (int i = 0; i < shown; i++) {
            sb.append(String.format("%02x", bytes[i] & 0xff));
        }
        if (bytes.length > shown) {
            sb.append("...");
        }
        return sb.toString();
    }

    private static void appendVarints(java.util.List<String> out, java.util.List<Long> values, String tag) {
        for (int i = 0; i < Math.min(values.size(), MAX_VALUES_PER_FIELD); i++) {
            out.add(tag + "=" + values.get(i));
        }
    }

    private static void appendFixed(java.util.List<String> out, java.util.List<? extends Number> values, String tag) {
        for (int i = 0; i < Math.min(values.size(), MAX_VALUES_PER_FIELD); i++) {
            out.add(tag + "=0x" + Long.toHexString(values.get(i).longValue()));
        }
    }

    private static boolean shouldDump(GameSession session, int opcode) {
        if (PacketOpcodes.BANNED_PACKETS.contains(opcode)) return false;
        return switch (GAME_INFO.logPackets) {
            case ALL -> !PacketOpcodesUtils.LOOP_PACKETS.contains(opcode) || GAME_INFO.isShowLoopPackets;
            case WHITELIST -> SERVER.debugWhitelist.contains(opcode);
            case BLACKLIST -> !SERVER.debugBlacklist.contains(opcode);
            default -> false;
        };
    }

}
