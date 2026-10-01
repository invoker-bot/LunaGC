package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.CardProductRewardNotifyOuterClass.CardProductRewardNotify;

public class PacketCardProductRewardNotify extends BasePacket {

    public PacketCardProductRewardNotify(int remainsDay) {
        this(remainsDay, 90);
    }

    public PacketCardProductRewardNotify(int remainsDay, int dailyReward) {
        super(PacketOpcodes.CardProductRewardNotify);

        CardProductRewardNotify proto =
                CardProductRewardNotify.newBuilder()
                        .setProductId("ys_chn_blessofmoon_tier5")
                        .setHcoin(dailyReward)
                        .setRemainDays(remainsDay)
                        .build();

        // Hard code Product id keep cool 😎

        this.setData(proto);
    }
}
