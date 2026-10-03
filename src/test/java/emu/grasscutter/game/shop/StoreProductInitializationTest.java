package emu.grasscutter.game.shop;

import static org.junit.jupiter.api.Assertions.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.PlayerRechargeDataNotifyOuterClass.PlayerRechargeDataNotify;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPlayerRechargeDataNotify;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoreProductInitializationTest {
    @TempDir Path dir;

    @Test
    void clientProductCatalogNotificationHasItsVerifiedOpcode() {
        // Client type 19066, get_CmdId at 0x1482930b0; the old value was an unsupported placeholder.
        assertEquals(
                9913,
                PacketOpcodes.PlayerRechargeDataNotify,
                "The SDK cannot request prices until the client receives its product IDs and tiers");
    }

    @Test
    void readsTheClientCatalogTagsRatherThanShopCardTags() throws Exception {
        // Reader 0x148293360: version tag 0x70, entries tag 0x7a.
        // Nested writer 0x152404430: product ID tag 0x42, tier tag 0x7a.
        byte[] wire = {0x70, 1, 0x7a, 6, 0x42, 1, 'c', 0x7a, 1, 't'};
        var catalog = PlayerRechargeDataNotify.parseFrom(wire);
        assertEquals(1, catalog.getProductPriceTierVersion());
        assertEquals(1, catalog.getProductPriceTierListCount());
        assertEquals("c", catalog.getProductPriceTierList(0).getProductId());
        assertEquals("t", catalog.getProductPriceTierList(0).getPriceTier());
        assertArrayEquals(wire, catalog.toByteArray());
    }

    @Test
    void catalogCoversNativeProductsAndMatchesTheSdkTierVersion() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var store = new FreeStore(new ShopCatalog(dir.resolve("ShopOverrides.json")));
        store.load();
        var packet = new PacketPlayerRechargeDataNotify(store);
        assertEquals(9913, packet.getOpcode());
        var catalog = PlayerRechargeDataNotify.parseFrom(packet.getData());
        var ids = new HashSet<String>();
        var tiers = new HashSet<String>();
        for (var product : catalog.getProductPriceTierListList()) {
            assertTrue(ids.add(product.getProductId()), "Duplicate SDK product");
            assertFalse(product.getPriceTier().isBlank());
            assertNotNull(store.forProductId(product.getProductId()));
            tiers.add(product.getPriceTier());
        }
        assertTrue(ids.contains("ys_chn_blessofmoon_tier5"));
        for (var kind : List.of("card", "crystals", "pass", "beyond_crystals")) {
            var products = store.products().stream().filter(p -> p.kind().equals(kind)).toList();
            assertFalse(products.isEmpty(), kind);
            for (var product : products) assertTrue(ids.contains(product.productId()), product.key());
        }
        assertFalse(ids.contains(""), "GM-only primogem grants are not native SDK products");
        assertEquals(
                Integer.toString(catalog.getProductPriceTierVersion()),
                store.priceTiers(false).get("price_tier_version"));
        @SuppressWarnings("unchecked")
        var sdkTiers = (List<Map<String, Object>>) store.priceTiers(false).get("tiers");
        assertEquals(tiers, new HashSet<>(sdkTiers.stream().map(t -> (String) t.get("tier_id")).toList()));

        store.setEnabled("card:101", false);
        var updated = PlayerRechargeDataNotify.parseFrom(new PacketPlayerRechargeDataNotify(store).getData());
        assertFalse(updated.getProductPriceTierListList().stream()
                .anyMatch(p -> p.getProductId().equals("ys_chn_blessofmoon_tier5")));
    }

    @Test
    void shopSystemCanSendTheCatalogWithoutAnyPurchase() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var packets = new ArrayList<BasePacket>();
        var session = new GameSession(null) {
            @Override
            public void send(BasePacket packet) {
                packets.add(packet);
            }
        };
        new ShopSystem(null).sendProductPriceCatalog(session);
        assertEquals(1, packets.size());
        assertEquals(9913, packets.get(0).getOpcode());
        assertTrue(PlayerRechargeDataNotify.parseFrom(packets.get(0).getData())
                .getProductPriceTierListCount() > 0);
    }
}
