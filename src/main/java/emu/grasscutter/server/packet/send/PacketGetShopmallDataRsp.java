package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.shop.ShopSystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetShopmallDataRspOuterClass.GetShopmallDataRsp;
import java.util.*;

public class PacketGetShopmallDataRsp extends BasePacket {

    public PacketGetShopmallDataRsp(ShopSystem shopSystem) {
        super(PacketOpcodes.GetShopmallDataRsp);

        // The tab list used to be hardcoded to List.of(900, 1052, 902, 1001, 903), which
        // was a 4.x snapshot: 900 and 903 no longer exist in the 7.0.0 shop excel at all,
        // and every category 7.0.0 added was invisible. ShopSystem loads exactly the shop
        // types present in the resources, so advertise those.
        List<Integer> shop_malls = new ArrayList<>(shopSystem.getShopData().keySet());
        Collections.sort(shop_malls);

        GetShopmallDataRsp proto =
                GetShopmallDataRsp.newBuilder().addAllShopTypeList(shop_malls).build();

        this.setData(proto);
    }
}
