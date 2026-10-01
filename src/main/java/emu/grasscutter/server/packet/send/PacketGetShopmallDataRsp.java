package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.shop.ShopSystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetShopmallDataRspOuterClass.GetShopmallDataRsp;
import java.util.*;

public class PacketGetShopmallDataRsp extends BasePacket {

    private static final List<Integer> SHOP_MALL_TABS = List.of(902, 1001, 1052, 903);

    public PacketGetShopmallDataRsp(ShopSystem shopSystem) {
        super(PacketOpcodes.GetShopmallDataRsp);

        // Only advertise tabs that are actually loaded, so a shopType with no goods never
        // becomes an empty page either.
        List<Integer> shop_malls = new ArrayList<>();
        for (int shopType : SHOP_MALL_TABS) {
            if (shopSystem.getShopData().containsKey(shopType)
                    || shopSystem.getFreeStore().products().stream()
                            .anyMatch(
                                    p ->
                                            shopSystem.getFreeStore().enabled(p)
                                                    && ((shopType == 902 && p.kind().equals("card"))
                                                            || (shopType == 903 && p.kind().equals("crystals"))))) {
                shop_malls.add(shopType);
            }
        }

        GetShopmallDataRsp proto =
                GetShopmallDataRsp.newBuilder().addAllShopTypeList(shop_malls).build();

        this.setData(proto);
    }
}
