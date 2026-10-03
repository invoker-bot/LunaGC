package emu.grasscutter.server.packet.recv;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.game.gacha.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.GachaWishReqOuterClass.GachaWishReq;
import emu.grasscutter.net.proto.GachaWishRspOuterClass.GachaWishRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GachaWishRequestTest {
    private static final long NOW = 1790810000L;
    private final Gson gson = new Gson();

    @BeforeAll
    static void configuration() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
    }

    private GachaBanner banner(int id) {
        var banner = gson.fromJson("{\"scheduleId\":" + id
                + ",\"bannerType\":\"WEAPON\",\"rateUpItems5\":[11501,15501]}", GachaBanner.class);
        banner.onLoad();
        return banner;
    }

    private GachaWishReq request(int schedule, int type, int item) {
        return GachaWishReq.newBuilder().setGachaScheduleId(schedule)
                .setGachaType(type).setItemId(item).build();
    }

    private GachaWishRsp apply(TestPlayer player, GachaBanner banner, GachaWishReq request) throws Exception {
        return GachaWishRsp.parseFrom(HandlerGachaWishReq.applyWish(player, banner, request, NOW).getData());
    }

    @Test
    void selectAndCancelPersistOnlyTheRequestedPath() throws Exception {
        var player = new TestPlayer();
        var first = banner(1001);
        var second = banner(1002);
        var a = apply(player, first, request(1001, 302, 11501));
        assertEquals(0, a.getRetcode());
        assertEquals(11501, a.getWishItemId());
        assertEquals(1001, a.getGachaScheduleId());
        assertEquals(0, a.getWishProgress());
        assertEquals(1, a.getWishMaxProgress());
        apply(player, second, request(1002, 302, 11501));
        player.getGachaInfo().getWishInfo(second).recordFiveStar(15501, 1);
        var cancelled = apply(player, first, request(1001, 302, 0));
        assertEquals(0, cancelled.getWishItemId());
        assertEquals(0, cancelled.getWishProgress());
        assertEquals(11501, player.getGachaInfo().getWishInfo(second).getWishItemId());
        assertEquals(1, player.getGachaInfo().getWishInfo(second).getFatePoints());
        assertEquals(3, player.saves);
    }

    @Test
    void selectingTheSameItemRetainsPointsButChangingItResetsThem() throws Exception {
        var player = new TestPlayer();
        var banner = banner(1001);
        apply(player, banner, request(1001, 302, 11501));
        player.getGachaInfo().getWishInfo(banner).recordFiveStar(15501, 1);
        assertEquals(1, apply(player, banner, request(1001, 302, 11501)).getWishProgress());
        var changed = apply(player, banner, request(1001, 302, 15501));
        assertEquals(15501, changed.getWishItemId());
        assertEquals(0, changed.getWishProgress());
    }

    @Test
    void foreignItemIsRejectedWithoutChangingAnExistingPath() throws Exception {
        var player = new TestPlayer();
        var banner = banner(1001);
        apply(player, banner, request(1001, 302, 11501));
        var rsp = apply(player, banner, request(1001, 302, 14501));
        assertEquals(Retcode.RET_GACHA_WISH_INVALID_ITEM.getNumber(), rsp.getRetcode());
        assertEquals(11501, player.getGachaInfo().getWishInfo(banner).getWishItemId());
        assertEquals(1, player.saves);
    }

    @Test
    void missingExpiredOrMismatchedBannerCannotBeChanged() throws Exception {
        var player = new TestPlayer();
        var banner = banner(1001);
        for (var req : new GachaWishReq[] {request(1002, 302, 11501), request(1001, 500, 11501)}) {
            assertEquals(Retcode.RET_GACHA_SCHEDULE_NOT_MATCH.getNumber(), apply(player, banner, req).getRetcode());
        }
        assertEquals(Retcode.RET_GACHA_SCHEDULE_NOT_MATCH.getNumber(),
                apply(player, null, request(1001, 302, 11501)).getRetcode());
        var expiredRow = gson.toJsonTree(banner).getAsJsonObject();
        expiredRow.addProperty("endTime", 1);
        var expired = gson.fromJson(expiredRow, GachaBanner.class);
        assertEquals(Retcode.RET_GACHA_SCHEDULE_NOT_MATCH.getNumber(),
                apply(player, expired, request(1001, 302, 11501)).getRetcode());
        assertEquals(0, player.saves);
    }

    private static class TestPlayer extends Player {
        private final PlayerGachaInfo wishes = new PlayerGachaInfo();
        private int saves;

        @Override
        public PlayerGachaInfo getGachaInfo() {
            return wishes;
        }

        @Override
        public void save() {
            saves++;
        }
    }
}
