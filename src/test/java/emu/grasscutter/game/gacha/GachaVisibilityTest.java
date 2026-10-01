package emu.grasscutter.game.gacha;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.DoGachaRspOuterClass.DoGachaRsp;
import emu.grasscutter.net.proto.GachaItemOuterClass.GachaItem;
import emu.grasscutter.net.proto.GetGachaInfoRspOuterClass.GetGachaInfoRsp;
import emu.grasscutter.server.packet.send.PacketDoGachaRsp;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GachaVisibilityTest {
  private static final long NOW = 1790810000L;
  private final Gson gson = new Gson();

  @BeforeAll
  static void configuration() throws Exception {
    Class.forName("emu.grasscutter.Grasscutter");
  }

  private GachaBanner banner(int id, String type, int limit) {
    var banner =
        gson.fromJson(
            "{\"scheduleId\":"
                + id
                + ",\"bannerType\":\""
                + type
                + "\",\"prefabPath\":\"test\",\"gachaTimesLimit\":"
                + limit
                + "}",
            GachaBanner.class);
    banner.onLoad();
    return banner;
  }

  private GachaBanner beginner() {
    return banner(803, "BEGINNER", 20);
  }

  private GetGachaInfoRsp list(TestPlayer player, GachaBanner... banners) {
    return GachaSystem.createProto(player, List.of(banners), NOW);
  }

  @Test
  void firstTenPullsKeepTheBeginnerBannerWithTenRemaining() {
    var player = new TestPlayer();
    var banner = beginner();
    assertEquals(20, list(player, banner).getGachaInfoList(0).getLeftGachaTimes());
    player.getGachaInfo().getBeginnerBanner().addTotalPulls(10);
    assertEquals(10, list(player, banner).getGachaInfoList(0).getLeftGachaTimes());
    assertTrue(banner.isActive(NOW));
  }

  @Test
  void twentiethPullRemovesOnlyTheCompletedPlayersBeginnerBanner() {
    var completed = new TestPlayer();
    var newcomer = new TestPlayer();
    var banner = beginner();
    completed.getGachaInfo().getBeginnerBanner().setTotalPulls(20);
    var standard = banner(893, "STANDARD", Integer.MAX_VALUE);
    assertEquals(
        List.of(893),
        list(completed, banner, standard).getGachaInfoListList().stream()
            .map(info -> info.getScheduleId())
            .toList());
    assertEquals(2, list(newcomer, banner, standard).getGachaInfoListCount());
    assertTrue(banner.isActive(NOW), "Player exhaustion must not disable the shared banner");
  }

  @Test
  void lastPullResponseReportsZeroAndTheRefreshedListOmitsTheBanner() throws Exception {
    var player = new TestPlayer();
    var banner = beginner();
    var state = player.getGachaInfo().getBeginnerBanner();
    state.setTotalPulls(10);
    state.addTotalPulls(10);
    var response =
        DoGachaRsp.parseFrom(
            new PacketDoGachaRsp(
                    banner, Collections.nCopies(10, GachaItem.getDefaultInstance()), state)
                .getData());
    assertEquals(10, response.getGachaTimes());
    assertEquals(0, response.getLeftGachaTimes());
    assertEquals(20, response.getGachaTimesLimit());
    assertEquals(0, list(player, banner).getGachaInfoListCount());
  }

  @Test
  void persistedCompletionAndNewScheduleIdsDoNotRestoreBeginnerPulls() {
    var original = new TestPlayer();
    original.getGachaInfo().getBeginnerBanner().setTotalPulls(20);
    var restored =
        new TestPlayer(gson.fromJson(gson.toJson(original.getGachaInfo()), PlayerGachaInfo.class));
    assertEquals(
        0, list(restored, beginner(), banner(1803, "BEGINNER", 20)).getGachaInfoListCount());
  }

  @Test
  void overLimitLegacyStateIsAlsoHidden() {
    var player = new TestPlayer();
    player.getGachaInfo().getBeginnerBanner().setTotalPulls(21);
    assertEquals(0, list(player, beginner()).getGachaInfoListCount());
  }

  @Test
  void finiteCustomBannersAreHiddenButUnlimitedBannersRemainVisible() {
    var player = new TestPlayer();
    player.getGachaInfo().getStandardBanner().setTotalPulls(Integer.MAX_VALUE);
    assertEquals(
        List.of(894),
        list(player, banner(893, "STANDARD", 5), banner(894, "STANDARD", Integer.MAX_VALUE))
            .getGachaInfoListList()
            .stream()
            .map(info -> info.getScheduleId())
            .toList());
  }

  @Test
  void incompletePlayerStillCannotSeeDisabledOrExpiredBanners() {
    var disabled =
        gson.fromJson(
            "{\"scheduleId\":803,\"bannerType\":\"BEGINNER\",\"disabled\":true}",
            GachaBanner.class);
    var expired =
        gson.fromJson(
            "{\"scheduleId\":804,\"bannerType\":\"BEGINNER\",\"endTime\":1}", GachaBanner.class);
    disabled.onLoad();
    expired.onLoad();
    assertEquals(0, list(new TestPlayer(), disabled, expired).getGachaInfoListCount());
  }

  private static class TestPlayer extends Player {
    private final PlayerGachaInfo wishes;
    private final Account testAccount = new Account();

    TestPlayer() {
      this(new PlayerGachaInfo());
    }

    TestPlayer(PlayerGachaInfo wishes) {
      this.wishes = wishes;
      testAccount.setSessionKey("visibility-test");
    }

    @Override
    public Account getAccount() {
      return testAccount;
    }

    @Override
    public PlayerGachaInfo getGachaInfo() {
      return wishes;
    }
  }
}
