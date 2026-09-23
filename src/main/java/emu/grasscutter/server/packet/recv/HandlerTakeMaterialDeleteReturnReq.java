package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MaterialDeleteReturnTypeOuterClass.MaterialDeleteReturnType;
import emu.grasscutter.net.proto.TakeMaterialDeleteReturnReqOuterClass.TakeMaterialDeleteReturnReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTakeMaterialDeleteReturnRsp;

// The client asks the server to hand back materials a delete operation owes it, and the type names
// where they should land: BAG into the inventory, SEED into the seed depot. The return tables are
// not wired up, so nothing is handed back yet -- but this request has a generated proto, unlike the
// harvest-only handlers, so the type is at least recorded for whatever implements the return later.
// The answer is an empty Rsp because its opcode ships in the 7.0 table without its .proto; the
// empty body is enough to leave the backlog, since a request the server rejects is still a request
// the client is waiting on.
@Opcodes(PacketOpcodes.TakeMaterialDeleteReturnReq)
public class HandlerTakeMaterialDeleteReturnReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = TakeMaterialDeleteReturnReq.parseFrom(payload);
        MaterialDeleteReturnType type = req.getType();

        var player = session.getPlayer();
        Grasscutter.getLogger()
                .debug(
                        "TakeMaterialDeleteReturnReq from uid {} - type {} ({})",
                        player != null ? player.getUid() : "an unlogged session",
                        type,
                        type.getNumber());

        session.send(new PacketTakeMaterialDeleteReturnRsp());
    }
}
