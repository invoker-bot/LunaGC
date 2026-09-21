package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

// Arrived from a live 7.0 client (2026-09-21, uid 10001) as {6:varint=1, 8:varint=2097155} -- an
// entity id and a flag, the client reporting that the camera entered an element-view. It is a Notify,
// so the client expects no answer. No generated proto, so neither field can be read. The handler
// exists to close the harvest loop: an opcode with a registered handler is no longer announced as
// unhandled, so the backlog stays a list of what is genuinely still missing.
@Opcodes(PacketOpcodes.AvatarEnterElementViewNotify)
public class HandlerAvatarEnterElementViewNotify extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
    }
}
