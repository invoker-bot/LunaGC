package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.shop.ShopSystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetShopmallDataRspOuterClass.GetShopmallDataRsp;
import java.util.*;

public class PacketGetShopmallDataRsp extends BasePacket {

    /// The shopTypes the client can render as a top-level mall tab. The client names every
    /// tab from its own client-side ShopTabConfig -- the server sends no shop text at all --
    /// so any shopType we advertise here that the client has no name for shows up as a blank
    /// page in the mall.
    ///
    /// This is the 4.x known-good list (900, 1052, 902, 1001, 903) minus the two types the
    /// 7.0.0 shop excel no longer defines: 900 (Paimon) and 903 (gift pack, duplicate of 902).
    /// Everything else ShopSystem loads must stay out of this list:
    ///   - 1002-1070+ are NPC/world shops, opened from NPC dialogue;
    ///   - 1037-1047, 15001-46001, 63001, 101000-104000 are activity shops, opened from their
    ///     own activity panels (and several only exist in the excel, with no curated table);
    ///   - 2000-2003 and 102000 are exchange sub-shops; the client resolves them by subTabId
    ///     from its own exchange config, and 1001 is the only exchange entry it needs here;
    ///   - 1048 (Serenitea Pot) is opened by Tubby inside the teapot, not from the mall.
    ///
    /// Restricting this list cannot lock anything out: HandlerGetShopReq serves whatever
    /// shopType the client asks for directly.
    private static final List<Integer> SHOP_MALL_TABS = List.of(902, 1001, 1052);

    public PacketGetShopmallDataRsp(ShopSystem shopSystem) {
        super(PacketOpcodes.GetShopmallDataRsp);

        // Only advertise tabs that are actually loaded, so a shopType with no goods never
        // becomes an empty page either.
        List<Integer> shop_malls = new ArrayList<>();
        for (int shopType : SHOP_MALL_TABS) {
            if (shopSystem.getShopData().containsKey(shopType)) {
                shop_malls.add(shopType);
            }
        }

        GetShopmallDataRsp proto =
                GetShopmallDataRsp.newBuilder().addAllShopTypeList(shop_malls).build();

        this.setData(proto);
    }
}
