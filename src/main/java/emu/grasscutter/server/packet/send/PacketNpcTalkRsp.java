package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.NpcTalkRspOuterClass.NpcTalkRsp;

public class PacketNpcTalkRsp extends BasePacket {
    public PacketNpcTalkRsp(int npcEntityId, int curTalkId, int entityId) {
        this(npcEntityId, curTalkId, entityId, 0);
    }

    public PacketNpcTalkRsp(int npcEntityId, int curTalkId, int entityId, int retcode) {
        super(PacketOpcodes.NpcTalkRsp);

        NpcTalkRsp p =
                NpcTalkRsp.newBuilder()
                        .setNpcEntityId(npcEntityId)
                        .setCurTalkId(curTalkId)
                        .setEntityId(entityId)
                        .setRetcode(retcode)
                        .build();

        this.setData(p);
    }
}
