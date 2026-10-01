package emu.grasscutter.game.shop;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.proto.*;
import org.junit.jupiter.api.Test;

/** Wire fixtures follow the verified client's reader/writer tags, not old fork schemas. */
class StoreProtocolTest {
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
}
