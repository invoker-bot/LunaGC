package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.shop.FreeStore;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerRechargeDataNotifyOuterClass.PlayerRechargeDataNotify;
import emu.grasscutter.net.proto.ProductPriceTierOuterClass.ProductPriceTier;

/** Seeds the native SDK's product query before it requests the price table. */
public class PacketPlayerRechargeDataNotify extends BasePacket {
    public PacketPlayerRechargeDataNotify(FreeStore store) {
        super(PacketOpcodes.PlayerRechargeDataNotify);
        var products =
                store.products().stream()
                        .filter(store::enabled)
                        .filter(p -> !p.productId().isBlank() && !p.priceTier().isBlank())
                        .map(p -> ProductPriceTier.newBuilder()
                                .setProductId(p.productId())
                                .setPriceTier(p.priceTier())
                                .build())
                        .distinct()
                        .toList();
        setData(PlayerRechargeDataNotify.newBuilder()
                .setProductPriceTierVersion(FreeStore.PRODUCT_PRICE_TIER_VERSION)
                .addAllProductPriceTierList(products));
    }
}
