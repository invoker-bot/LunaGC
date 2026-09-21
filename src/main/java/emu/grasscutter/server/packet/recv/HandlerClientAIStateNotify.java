package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ClientAIStateNotifyOuterClass.ClientAIStateNotify;
import emu.grasscutter.server.game.GameSession;

// Client-side AI state report. There is no Rsp for this opcode -- the client
// only wants the server to know which tactic an entity switched to -- so the
// handler parses and logs it rather than leaving the opcode unhandled.
@Opcodes(PacketOpcodes.ClientAIStateNotify)
public class HandlerClientAIStateNotify extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        ClientAIStateNotify notify = ClientAIStateNotify.parseFrom(payload);
    }
}
