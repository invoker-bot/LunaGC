package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.shop.ShopSystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetShopmallDataRspOuterClass.GetShopmallDataRsp;
import emu.grasscutter.utils.Utils;
import java.util.*;

public class PacketGetShopmallDataRsp extends BasePacket {

    private static final List<Integer> SHOP_MALL_TABS = List.of(900, 902, 1001, 1052, 903);
    private static final List<Integer> BEYOND_MALL_TABS =
            List.of(104000, 103000, 102000, 101000, 100000, 105000);

    public PacketGetShopmallDataRsp(ShopSystem shopSystem) {
        this(shopSystem, 0, 0);
    }

    public PacketGetShopmallDataRsp(ShopSystem shopSystem, int selectedShopType, int param) {
        super(PacketOpcodes.GetShopmallDataRsp);

        // Only advertise tabs that are actually loaded, so a shopType with no goods never
        // becomes an empty page either.
        List<Integer> shop_malls = new ArrayList<>();
        boolean beyond = BEYOND_MALL_TABS.contains(selectedShopType);
        var tabs = beyond ? BEYOND_MALL_TABS : SHOP_MALL_TABS;
        int now = Utils.getCurrentSeconds();
        for (int shopType : tabs) {
            boolean hasGoods = shopSystem.getShopData().containsKey(shopType);
            if (hasGoods && beyond) {
                hasGoods =
                        shopSystem.getShopData().get(shopType).stream()
                                .anyMatch(
                                        g -> {
                                            var item = g.resolveGoodsItem(now);
                                            return now >= g.getBeginTime()
                                                    && now < g.getEndTime()
                                                    && item != null
                                                    && item.getId() > 0
                                                    && item.getCount() > 0;
                                        });
            }
            if (hasGoods
                    || shopSystem.getFreeStore().products().stream()
                            .anyMatch(
                                    p ->
                                            shopSystem.getFreeStore().enabled(p)
                                                    && (((shopType == 900 || shopType == 902) && p.kind().equals("card"))
                                                            || (shopType == 903 && p.kind().equals("crystals"))
                                                            || (shopType == 100000 && p.kind().equals("beyond_crystals"))))) {
                shop_malls.add(shopType);
            }
        }

        GetShopmallDataRsp proto =
                GetShopmallDataRsp.newBuilder()
                        .setShopType(selectedShopType)
                        .setParam(param)
                        .addAllShopTypeList(shop_malls)
                        .build();

        this.setData(proto);
    }
}
