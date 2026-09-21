package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ReliquaryUpgradeRspOuterClass.ReliquaryUpgradeRsp;
import java.util.List;

public class PacketReliquaryUpgradeRsp extends BasePacket {

    /**
     * The answer to a {@code ReliquaryUpgradeRsp} the server rejected.
     *
     * <p>Without this the client keeps waiting on a response that never comes, which is what the
     * in-game error panel is: not a crash, not a server fault logged anywhere - simply a request
     * the server abandoned without telling the player. See
     * {@link emu.grasscutter.game.systems.InventorySystem#upgradeRelic}.
     *
     * @param retcode The reason, from {@link emu.grasscutter.net.proto.RetcodeOuterClass.Retcode}.
     */
    public PacketReliquaryUpgradeRsp(int retcode) {
        super(PacketOpcodes.ReliquaryUpgradeRsp);

        this.setData(ReliquaryUpgradeRsp.newBuilder().setRetcode(retcode).build());
    }

    public PacketReliquaryUpgradeRsp(
            GameItem relic, int rate, int oldLevel, List<Integer> oldAppendPropIdList) {
        super(PacketOpcodes.ReliquaryUpgradeRsp);

        ReliquaryUpgradeRsp proto =
                ReliquaryUpgradeRsp.newBuilder()
                        .setTargetReliquaryGuid(relic.getGuid())
                        .setOldLevel(oldLevel)
                        .setCurLevel(relic.getLevel())
                        .setPowerUpRate(rate)
                        .addAllOldAppendPropList(oldAppendPropIdList)
                        .addAllCurAppendPropList(relic.getAppendPropIdList())
                        .build();

        this.setData(proto);
    }
}
