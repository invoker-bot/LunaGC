package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

// Client-side AI state report. There is no Rsp for this opcode -- the client
// only wants the server to know which tactic an entity switched to -- so the
// handler acknowledges it without interpreting the version-specific payload.
@Opcodes(PacketOpcodes.ClientAIStateNotify)
public class HandlerClientAIStateNotify extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        // 7.1 does not include a named ClientAIStateNotify proto in the upstream dump.
    }
}
