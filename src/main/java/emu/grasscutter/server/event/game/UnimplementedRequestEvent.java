package emu.grasscutter.server.event.game;

import emu.grasscutter.server.event.Cancellable;
import emu.grasscutter.server.event.types.ServerEvent;
import emu.grasscutter.server.game.GameSession;

/**
 * Fired when the game server decides a packet it just received is probably not implemented.
 *
 * <p>The three reasons describe every way a player's action can silently do nothing:
 *
 * <ul>
 *   <li>{@link Reason#NO_HANDLER} - no {@code PacketHandler} is registered for the opcode at all.
 *   The client asked for a feature the server has no code for.
 *   <li>{@link Reason#HANDLER_THREW} - a handler exists but raised, so the action was abandoned
 *   halfway. Already logged as an error; this event lets a listener collect it alongside the
 *   others.
 *   <li>{@link Reason#NO_RESPONSE} - the handler ran to completion and returned without sending a
 *   single packet back. This one is what an in-game error dialog usually means: the client is
 *   waiting on the answer to its request and never gets one.
 * </ul>
 *
 * <p>The event always fires, but the listener that acts on it is only registered in developer mode
 * ({@code -dev}); see
 * {@link emu.grasscutter.server.dev.UnimplementedRequestReporter}. Cancel it to
 * stop that listener from filing a report, e.g. when a plugin implements the feature itself.
 */
public final class UnimplementedRequestEvent extends ServerEvent implements Cancellable {

    /** How the server decided this request is probably unimplemented. */
    public enum Reason {
        /** No packet handler is registered for this opcode. */
        NO_HANDLER("no PacketHandler is registered for this opcode"),
        /** A handler exists, but it raised while handling the request. */
        HANDLER_THREW("the handler raised while handling the request"),
        /** The handler returned without ever answering the client. */
        NO_RESPONSE("the handler returned without sending a packet back");

        private final String description;

        Reason(String description) {
            this.description = description;
        }

        public String getDescription() {
            return this.description;
        }
    }

    private final GameSession session;
    private final int opcode;
    private final byte[] payload;
    private final String fields;
    private final Reason reason;
    private final String detail;

    public UnimplementedRequestEvent(
            GameSession session, int opcode, byte[] payload, String fields, Reason reason, String detail) {
        super(Type.GAME);

        this.session = session;
        this.opcode = opcode;
        this.payload = payload;
        this.fields = fields;
        this.reason = reason;
        this.detail = detail;
    }

    /** The session the request arrived on. May be pre-login, so {@link #getPlayer()} can be null. */
    public GameSession getSession() {
        return this.session;
    }

    /** Convenience for {@link GameSession#getPlayer()}; null before login completes. */
    public emu.grasscutter.game.player.Player getPlayer() {
        return this.session.getPlayer();
    }

    public int getOpcode() {
        return this.opcode;
    }

    public byte[] getPayload() {
        return this.payload;
    }

    /** The payload rendered as {@code {no:wire=value, ...}}, or {@code <unparsed>} / empty. */
    public String getFields() {
        return this.fields;
    }

    public Reason getReason() {
        return this.reason;
    }

    /** The exception's class and message for {@link Reason#HANDLER_THREW}, the handler's class
     * name otherwise. */
    public String getDetail() {
        return this.detail;
    }
}
