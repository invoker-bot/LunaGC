package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

// Client pathfinding telemetry. There is no Rsp for this opcode and no generated proto on this
// version, so the payload is left unparsed and nothing is returned: the constant in PacketOpcodes
// is an inference from the block layout rather than a dump lookup, so parsing against a shape we
// do not actually have would be worse than dropping it. What matters is that the opcode stops
// filling the unhandled backlog.
@Opcodes(PacketOpcodes.PathfindingPingNotify)
public class HandlerPathfindingPingNotify extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
    }
}
