package emu.grasscutter.game.gacha;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.mongodb.client.MongoClients;
import dev.morphia.Morphia;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.mapping.MapperOptions;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.DoGachaRspOuterClass.DoGachaRsp;
import emu.grasscutter.server.packet.send.PacketDoGachaRsp;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GachaPathIsolationTest {
    private final Gson gson = new Gson();
    private static GachaSystem system;

    @BeforeAll
    static void configuration() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        system = new GachaSystem(null) {
            @Override
            public synchronized boolean load() {
                return true;
            }

            @Override
            public int randomRange(int min, int max) {
                return min;
            }
        };
    }

    private GachaBanner weapon(int schedule, int begin, int... featured) {
        var banner = gson.fromJson(
                "{\"scheduleId\":" + schedule + ",\"bannerType\":\"WEAPON\",\"prefabPath\":\"test\",\"beginTime\":" + begin
                        + ",\"rateUpItems5\":" + gson.toJson(featured) + "}", GachaBanner.class);
        banner.onLoad();
        return banner;
    }

    @Test
    void choosingASharedWeaponDoesNotSetThePathInAnotherBanner() {
        var player = new TestPlayer();
        var first = weapon(1001, 1, 11501, 15501);
        var second = weapon(1002, 1, 11501, 14501);
        var state = player.getGachaInfo().getWishInfo(first);
        state.selectItem(11501);
        state.recordFiveStar(15501, first.getWishMaxProgress());
        assertEquals(11501, first.toProto(player).getWishItemId());
        assertEquals(1, first.toProto(player).getWishProgress());
        assertEquals(0, second.toProto(player).getWishItemId());
        assertEquals(0, second.toProto(player).getWishProgress());
    }

    @Test
    void oldSharedPathCannotNameAWeaponOutsideTheCurrentFeaturedPool() {
        var player = new TestPlayer();
        player.getGachaInfo().getEventWeaponBanner().setWishItemId(15501);
        player.getGachaInfo().getEventWeaponBanner().setFailedChosenItemPulls(1);
        var current = weapon(1002, 1, 11501, 14501);
        assertEquals(0, current.toProto(player).getWishItemId());
        assertEquals(0, current.toProto(player).getWishProgress());
    }

    @Test
    void identicalPoolsWithDifferentScheduleIdsStillHaveSeparatePaths() {
        var player = new TestPlayer();
        var first = weapon(1001, 1, 11501, 15501);
        var second = weapon(1002, 1, 11501, 15501);
        var state = player.getGachaInfo().getWishInfo(first);
        state.selectItem(11501);
        state.recordFiveStar(15501, 1);
        assertEquals(0, second.toProto(player).getWishItemId());
        assertFalse(state.matches(second));
    }

    @Test
    void cancellingOrChangingOnePathLeavesTheOtherPathAndPityAlone() {
        var wishes = new PlayerGachaInfo();
        var first = weapon(1001, 1, 11501, 15501);
        var second = weapon(1002, 1, 11501, 14501);
        var pity = wishes.getBannerInfo(first);
        pity.setPity5(37);
        pity.setPity4(6);
        pity.setFailedFeaturedItemPulls(5, 1);
        var a = wishes.getWishInfo(first);
        var b = wishes.getWishInfo(second);
        a.selectItem(11501);
        b.selectItem(11501);
        a.recordFiveStar(15501, 1);
        b.recordFiveStar(14501, 1);
        a.selectItem(11501);
        assertEquals(1, a.getFatePoints(), "Repeated selection retains progress");
        a.selectItem(15501);
        assertEquals(0, a.getFatePoints());
        a.recordFiveStar(11501, 1);
        a.selectItem(0);
        assertEquals(0, a.getWishItemId());
        assertEquals(0, a.getFatePoints());
        assertEquals(11501, b.getWishItemId());
        assertEquals(1, b.getFatePoints());
        assertSame(pity, wishes.getBannerInfo(second));
        assertEquals(37, pity.getPity5());
        assertEquals(6, pity.getPity4());
        assertEquals(1, pity.getFailedFeaturedItemPulls(5));
    }

    @Test
    void extendingEndTimeRetainsThePathButANewRunResetsIt() {
        var wishes = new PlayerGachaInfo();
        var original = weapon(1001, 1, 11501, 15501);
        var state = wishes.getWishInfo(original);
        state.selectItem(11501);
        state.recordFiveStar(15501, 1);
        var extended = gson.fromJson(gson.toJson(original), GachaBanner.class);
        var end = gson.toJsonTree(extended).getAsJsonObject();
        end.addProperty("endTime", 1924992001);
        extended = gson.fromJson(end, GachaBanner.class);
        extended.onLoad();
        assertSame(state, wishes.getWishInfo(extended));
        var rerun = wishes.getWishInfo(weapon(1001, 2, 11501, 15501));
        assertEquals(0, rerun.getWishItemId());
        assertEquals(0, rerun.getFatePoints());
    }

    @Test
    void changingTheFeaturedPoolResetsEvenAWeaponSharedByBothPools() {
        var wishes = new PlayerGachaInfo();
        var first = wishes.getWishInfo(weapon(1001, 1, 11501, 15501));
        first.selectItem(11501);
        first.recordFiveStar(15501, 1);
        var edited = wishes.getWishInfo(weapon(1001, 1, 11501, 14501));
        assertEquals(0, edited.getWishItemId());
        assertEquals(0, edited.getFatePoints());
    }

    @Test
    void reorderingTheSamePoolDoesNotLoseThePath() {
        var wishes = new PlayerGachaInfo();
        var first = wishes.getWishInfo(weapon(1001, 1, 11501, 15501));
        first.selectItem(11501);
        assertSame(first, wishes.getWishInfo(weapon(1001, 1, 15501, 11501)));
    }

    @Test
    void weaponAndChroniclePathsDoNotShareAnOwner() {
        var wishes = new PlayerGachaInfo();
        var first = weapon(1001, 1, 11501, 15501);
        var row = gson.toJsonTree(first).getAsJsonObject();
        row.addProperty("bannerType", "CHRONICLE");
        row.addProperty("gachaType", 500);
        var chronicle = gson.fromJson(row, GachaBanner.class);
        chronicle.onLoad();
        wishes.getWishInfo(first).selectItem(11501);
        assertEquals(0, wishes.getWishInfo(chronicle).getWishItemId());
    }

    @Test
    void pullResponsesReportOnlyTheSelectedBannersPath() throws Exception {
        var wishes = new PlayerGachaInfo();
        var first = weapon(1001, 1, 11501, 15501);
        var second = weapon(1002, 1, 11501, 14501);
        var path = wishes.getWishInfo(first);
        path.selectItem(11501);
        path.recordFiveStar(15501, 1);
        var a = DoGachaRsp.parseFrom(new PacketDoGachaRsp(first, List.of(),
                wishes.getBannerInfo(first), path).getData());
        var b = DoGachaRsp.parseFrom(new PacketDoGachaRsp(second, List.of(),
                wishes.getBannerInfo(second), wishes.getWishInfo(second)).getData());
        assertEquals(11501, a.getWishItemId());
        assertEquals(1, a.getWishProgress());
        assertEquals(1002, b.getGachaScheduleId());
        assertEquals(0, b.getWishItemId());
        assertEquals(0, b.getWishProgress());
    }

    @Test
    void oldSharedStateIsIgnoredWithoutErasingThePlayersPullCountAndPity() {
        var old = gson.fromJson("{\"eventWeaponBanner\":{\"totalPulls\":143,\"pity5\":37,"
                + "\"pity4\":6,\"failedFeaturedItemPulls\":1,\"wishItemId\":11501,"
                + "\"failedChosenItemPulls\":1},\"bannerWishes\":null}", PlayerGachaInfo.class);
        var banner = weapon(1001, 1, 11501, 15501);
        assertEquals(0, old.getWishInfo(banner).getWishItemId());
        assertEquals(0, old.getWishInfo(banner).getFatePoints());
        assertEquals(143, old.getBannerInfo(banner).getTotalPulls());
        assertEquals(37, old.getBannerInfo(banner).getPity5());
        assertEquals(6, old.getBannerInfo(banner).getPity4());
        assertEquals(1, old.getBannerInfo(banner).getFailedFeaturedItemPulls(5));
    }

    @Test
    void unchosenBannerDrawsItsOwnPoolDespiteAnotherBannersFullPath() {
        var wishes = new PlayerGachaInfo();
        var first = weapon(1001, 1, 15501, 11501);
        var second = weapon(1002, 1, 14501, 11501);
        var path = wishes.getWishInfo(first);
        path.selectItem(11501);
        path.recordFiveStar(15501, 1);
        var pity = wishes.getBannerInfo(second);
        // Reproduces a migrated save with an unrelated full type-wide path.
        pity.setWishItemId(15501);
        pity.setFailedChosenItemPulls(1);
        var result = system.doRarePull(second.getRateUpItems5(), new int[0], new int[0], 5,
                second, pity, wishes.getWishInfo(second));
        assertEquals(14501, result.itemId(), "First normal featured result, not either stale chosen item");
        assertEquals(1, path.getFatePoints());
        assertEquals(0, wishes.getWishInfo(second).getFatePoints());
    }

    @Test
    void fullPathForcesOnlyItsOwnWeaponThenResetsOnlyItsOwnPoints() {
        var wishes = new PlayerGachaInfo();
        var first = weapon(1001, 1, 15501, 11501);
        var second = weapon(1002, 1, 14501, 11501);
        var a = wishes.getWishInfo(first);
        var b = wishes.getWishInfo(second);
        a.selectItem(11501);
        b.selectItem(11501);
        a.recordFiveStar(15501, 1);
        b.recordFiveStar(14501, 1);
        var result = system.doRarePull(first.getRateUpItems5(), new int[0], new int[0], 5,
                first, wishes.getBannerInfo(first), a);
        assertEquals(11501, result.itemId(), "Must force the chosen item instead of the first random item");
        assertEquals(0, a.getFatePoints());
        assertEquals(11501, a.getWishItemId());
        assertEquals(1, b.getFatePoints());
    }

    @Test
    void rarePullDefensivelyRejectsForeignOrInvalidPathState() {
        var first = weapon(1001, 1, 15501, 11501);
        var second = weapon(1002, 1, 14501, 11501);
        var foreign = new PlayerGachaWishInfo(first);
        foreign.selectItem(11501);
        foreign.recordFiveStar(15501, 1);
        assertEquals(14501, system.doRarePull(second.getRateUpItems5(), new int[0], new int[0], 5,
                second, new PlayerGachaBannerInfo(), foreign).itemId());
        var corrupt = new PlayerGachaWishInfo(second);
        corrupt.selectItem(15501);
        corrupt.recordFiveStar(14501, 1);
        assertEquals(14501, system.doRarePull(second.getRateUpItems5(), new int[0], new int[0], 5,
                second, new PlayerGachaBannerInfo(), corrupt).itemId());
    }

    @Test
    void ordinaryMissBuildsOnlyThisPathAndFourStarPullsLeaveItAlone() {
        var banner = weapon(1001, 1, 15501, 11501);
        var path = new PlayerGachaWishInfo(banner);
        path.selectItem(11501);
        assertEquals(15501, system.doRarePull(banner.getRateUpItems5(), new int[0], new int[0], 5,
                banner, new PlayerGachaBannerInfo(), path).itemId());
        assertEquals(1, path.getFatePoints());
        system.doRarePull(new int[] {11401}, new int[0], new int[0], 4,
                banner, new PlayerGachaBannerInfo(), path);
        assertEquals(1, path.getFatePoints());
    }

    @Entity(value = "gacha_path_snapshot", useDiscriminator = false)
    public static class Snapshot {
        @Id private String id = "player";
        private PlayerGachaInfo gachaInfo;

        public Snapshot() {}
    }

    @Test
    void independentPathsAndSharedPitySurviveTheProductionMongoCodec() {
        var snapshot = new Snapshot();
        snapshot.gachaInfo = new PlayerGachaInfo();
        var first = weapon(1001, 1, 11501, 15501);
        var second = weapon(1002, 1, 11501, 14501);
        snapshot.gachaInfo.getBannerInfo(first).setPity5(37);
        snapshot.gachaInfo.getWishInfo(first).selectItem(11501);
        snapshot.gachaInfo.getWishInfo(first).recordFiveStar(15501, 1);
        snapshot.gachaInfo.getWishInfo(second).selectItem(14501);
        // BSON conversion only: no database writes or server are needed.
        try (var client = MongoClients.create("mongodb://127.0.0.1:1/?serverSelectionTimeoutMS=100")) {
            var datastore = Morphia.createDatastore(client, "gacha_path_codec_test",
                    MapperOptions.builder().storeEmpties(true).storeNulls(false).build());
            var mapper = datastore.getMapper();
            mapper.map(Snapshot.class, PlayerGachaInfo.class, PlayerGachaBannerInfo.class,
                    PlayerGachaWishInfo.class);
            var restored = mapper.fromDocument(Snapshot.class, mapper.toDocument(snapshot)).gachaInfo;
            assertEquals(11501, restored.getWishInfo(first).getWishItemId());
            assertEquals(1, restored.getWishInfo(first).getFatePoints());
            assertEquals(14501, restored.getWishInfo(second).getWishItemId());
            assertEquals(0, restored.getWishInfo(second).getFatePoints());
            assertEquals(37, restored.getBannerInfo(second).getPity5());
        }
    }

    private static class TestPlayer extends Player {
        private final Account account = new Account();
        private final PlayerGachaInfo wishes = new PlayerGachaInfo();

        TestPlayer() {
            account.setSessionKey("path-test");
        }

        @Override
        public Account getAccount() {
            return account;
        }

        @Override
        public PlayerGachaInfo getGachaInfo() {
            return wishes;
        }

        @Override
        public void save() {}
    }
}
