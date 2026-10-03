package emu.grasscutter.game.shop;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.proto.*;
import org.junit.jupiter.api.Test;

/** Wire fixtures follow the verified client's reader/writer tags, not old fork schemas. */
class StoreProtocolTest {
    @Test
    void beyondMallRequestCarriesSelectedShopAndResponseEchoesIt() throws Exception {
        // Sender 0x147256a80 and parser for client type 14262.
        var req =
                GetShopmallDataReqOuterClass.GetShopmallDataReq.parseFrom(
                        new byte[] {0x50, 1, 0x70, (byte) 0xf0, (byte) 0x9c, 6});
        var shopField = req.getDescriptorForType().findFieldByName("shop_type");
        assertNotNull(shopField, "7.1 mall requests are not empty");
        assertEquals(14, shopField.getNumber());
        assertEquals(102000, req.getField(shopField));
        var rsp =
                GetShopmallDataRspOuterClass.GetShopmallDataRsp.parseFrom(
                        new byte[] {0x58, (byte) 0xf0, (byte) 0x9c, 6, 0x78, 1});
        assertEquals(1, rsp.getParam(), "the response parameter is field 15, not field 11");
    }

    @Test
    void beyondMallOnlyAdvertisesBeyondCategoriesAndEchoesSelection() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var manager = new ShopSystem(null);
        var base = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<java.util.List<ShopInfo>>();
        var live = new ShopInfo();
        live.setGoodsItem(new emu.grasscutter.data.common.ItemParamData(262502, 1));
        base.put(102000, java.util.List.of(live));
        var expired = new ShopInfo();
        expired.setGoodsItem(new emu.grasscutter.data.common.ItemParamData(263935, 1));
        expired.setEndTime(1);
        base.put(104000, java.util.List.of(expired));
        manager.getCatalog().replaceBase(base);
        var rsp =
                GetShopmallDataRspOuterClass.GetShopmallDataRsp.parseFrom(
                        new emu.grasscutter.server.packet.send.PacketGetShopmallDataRsp(manager, 102000, 1)
                                .getData());
        assertEquals(102000, rsp.getShopType());
        assertEquals(1, rsp.getParam());
        assertTrue(rsp.getShopTypeListList().contains(102000));
        assertTrue(rsp.getShopTypeListList().contains(100000));
        assertFalse(rsp.getShopTypeListList().contains(900));
        assertFalse(rsp.getShopTypeListList().contains(1001));
        assertFalse(rsp.getShopTypeListList().contains(104000));
    }

    @Test
    void beyondPriceAndRechargeHaveDistinctClientTags() throws Exception {
        // ShopGoods reader: tag 0x2160 -> cost for virtual item 231 (field 1068).
        var goods =
                ShopGoodsOuterClass.ShopGoods.parseFrom(new byte[] {(byte) 0xe0, 0x42, (byte) 0xe8, 7});
        assertEquals(1000, goods.getBeyondMcoin());
        assertEquals(0, goods.getMcoin());
        var req = RechargeReqOuterClass.RechargeReq.parseFrom(new byte[] {0x1a, 3, 0x0a, 1, 'b'});
        assertTrue(req.hasBeyondMcoinProduct());
        assertEquals("b", req.getBeyondMcoinProduct().getProductId());
        assertFalse(req.hasMcoinProduct());
    }

    @Test
    void goodsUseNativeLevelLimitAndCurrencyFields() throws Exception {
        // Reader 0x14c846ab0, consumer 0x149a58d60 and limit check 0x149a57404.
        var goods =
                ShopGoodsOuterClass.ShopGoods.parseFrom(new byte[] {0x58, 4, 0x70, 5, 0x48, 100, 0x78, 75});
        assertEquals(4, goods.getMinLevel());
        assertEquals(5, goods.getSingleLimit());
        assertEquals(100, goods.getScoin());
        assertEquals(75, goods.getMcoin());
    }

    @Test
    void recommendedAndPaimonEntrancesMatchTheNativeShopEnum() {
        assertEquals(900, ShopType.SHOP_TYPE_RECOMMEND.shopTypeId);
        assertEquals(1001, ShopType.SHOP_TYPE_PAIMON.shopTypeId);
    }

    @Test
    void monthlyCardEnablesTheRecommendedEntrance() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var manager = new ShopSystem(null);
        var rsp =
                GetShopmallDataRspOuterClass.GetShopmallDataRsp.parseFrom(
                        new emu.grasscutter.server.packet.send.PacketGetShopmallDataRsp(manager).getData());
        assertTrue(rsp.getShopTypeListList().containsAll(java.util.List.of(900, 902, 903, 1001)));
    }

    @Test
    void recommendedPageHasTheCardInItsOwnProductLookup(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var manager = new ShopSystem(null);
        var player =
                new emu.grasscutter.game.player.Player() {
                    @Override
                    public void save() {}
                };
        // 0x149000729 selects shop 900 before looking up recommendation config 101.
        var recommended =
                emu.grasscutter.server.packet.send.PacketGetShopRsp.buildShop(player, manager, 900);
        var packages =
                emu.grasscutter.server.packet.send.PacketGetShopRsp.buildShop(player, manager, 902);
        assertEquals(1, recommended.getCardProductListCount());
        assertEquals(packages.getCardProductListList(), recommended.getCardProductListList());
        assertEquals("ys_chn_blessofmoon_tier5", recommended.getCardProductList(0).getProductId());
        var store = new FreeStore(new ShopCatalog(dir.resolve("ShopOverrides.json")));
        store.load();
        store.setEnabled("card:101", false);
        for (int type : java.util.List.of(900, 902)) {
            var shop = ShopOuterClass.Shop.newBuilder().setShopType(type);
            store.addProducts(shop, player);
            assertEquals(0, shop.getCardProductListCount());
        }
    }

    @Test
    void batchRequestReadsTheClientsPackedFieldEleven() throws Exception {
        var req =
                GetShopBatchReqOuterClass.GetShopBatchReq.parseFrom(
                        new byte[] {0x5a, 4, (byte) 0x86, 7, (byte) 0x87, 7});
        assertEquals(java.util.List.of(902, 903), req.getShopTypeListList());
    }

    @Test
    void cardAndCrystalsUseTheClientsDistinctLists() throws Exception {
        // field 1: card with product ID "c"; field 9: crystals with product ID "m".
        var shop =
                ShopOuterClass.Shop.parseFrom(new byte[] {0x0a, 3, 0x0a, 1, 'c', 0x4a, 3, 0x0a, 1, 'm'});
        assertEquals("c", shop.getCardProductList(0).getProductId());
        assertEquals("m", shop.getMcoinProductList(0).getProductId());
    }

    @Test
    void batchResponseHasShopFieldFourAndRetcodeFieldTen() throws Exception {
        var rsp =
                GetShopBatchRspOuterClass.GetShopBatchRsp.parseFrom(
                        new byte[] {0x22, 3, 0x40, (byte) 0x86, 7, 0x50, 1});
        assertEquals(902, rsp.getShopList(0).getShopType());
        assertEquals(1, rsp.getRetcode());
    }

    @Test
    void rechargeRequestReadsOnlyTheSupportedProducts() throws Exception {
        var req = RechargeReqOuterClass.RechargeReq.parseFrom(new byte[] {0x62, 3, 0x0a, 1, 'c'});
        assertTrue(req.hasCardProduct());
        assertEquals("c", req.getCardProduct().getProductId());
        var rsp = RechargeRspOuterClass.RechargeRsp.parseFrom(new byte[] {0x28, 1, 0x62, 1, 'c'});
        assertEquals(1, rsp.getRetcode());
        assertEquals("c", rsp.getProductId());
    }

    @Test
    void battlePassQueryUsesVerifiedRequestAndResponseTags() throws Exception {
        var req =
                GetBattlePassProductReqOuterClass.GetBattlePassProductReq.parseFrom(new byte[] {0x48, 2});
        assertEquals(2, req.getPlayType());
        var rsp =
                GetBattlePassProductRspOuterClass.GetBattlePassProductRsp.parseFrom(
                        new byte[] {0x22, 1, '0', 0x30, 2, 0x7a, 1, 'p'});
        assertEquals("0", rsp.getPriceTier());
        assertEquals(2, rsp.getPlayType());
        assertEquals("p", rsp.getProductId());
    }

    @Test
    void battlePassMissionClaimAndWeeklyPointsUseClientFields() throws Exception {
        var req =
                TakeBattlePassMissionPointReqOuterClass.TakeBattlePassMissionPointReq.parseFrom(
                        new byte[] {0x22, 2, 1, 2});
        assertEquals(java.util.List.of(1, 2), req.getMissionIdListList());
        assertEquals(23648, emu.grasscutter.net.packet.PacketOpcodes.TakeBattlePassMissionPointReq);
        var rsp =
                TakeBattlePassMissionPointRspOuterClass.TakeBattlePassMissionPointRsp.parseFrom(
                        new byte[] {0x40, 1, 0x6a, 2, 1, 2});
        assertEquals(1, rsp.getRetcode());
        assertEquals(java.util.List.of(1, 2), rsp.getMissionIdListList());
        var schedule =
                BattlePassScheduleOuterClass.BattlePassSchedule.parseFrom(new byte[] {0x48, 8, 0x78, 120});
        assertEquals(8, schedule.getPaidPlatformFlags());
        assertEquals(120, schedule.getCurCyclePoints());
    }
}
