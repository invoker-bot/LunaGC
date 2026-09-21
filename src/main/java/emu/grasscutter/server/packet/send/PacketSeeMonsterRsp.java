package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SeeMonsterRspOuterClass.SeeMonsterRsp;

// SeeMonsterReq/Rsp is a two-way handshake the 7.0.0 client sends when a monster
// enters its view. There is no server-side state to update for a plain "seen"
// report, so the answer is the empty success Rsp; the point is that the opcode
// stops appearing in harvest-opcodes.txt.
public class PacketSeeMonsterRsp extends BasePacket {

    public PacketSeeMonsterRsp() {
        super(PacketOpcodes.SeeMonsterRsp);

        SeeMonsterRsp proto = SeeMonsterRsp.newBuilder().build();

        this.setData(proto);
    }
}
