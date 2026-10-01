package emu.grasscutter.game.shop;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.battlepass.BattlePassSystem;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.proto.BattlePassRewardTagOuterClass.BattlePassRewardTag;
import emu.grasscutter.net.proto.BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption;
import emu.grasscutter.net.proto.BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus;
import emu.grasscutter.utils.*;
import java.util.*;
import org.junit.jupiter.api.*;

class BattlePassFlowTest {
    private final Gson gson = new Gson();
    private Map<Integer, BattlePassMissionData> missions;
    private Map<Integer, BattlePassScheduleData> schedules;
    private Map<Integer, BattlePassRewardData> levels;
    private Map<Integer, RewardData> rewards;
    private ItemData item;

    @BeforeEach
    void resources() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        missions = new HashMap<>(GameData.getBattlePassMissionDataMap());
        schedules = new HashMap<>(GameData.getBattlePassScheduleDataMap());
        levels = new HashMap<>(GameData.getBattlePassRewardDataMap());
        rewards = new HashMap<>(GameData.getRewardDataMap());
        GameData.getBattlePassMissionDataMap().clear();
        GameData.getBattlePassScheduleDataMap().clear();
        GameData.getBattlePassScheduleDataMap()
                .put(
                        7100,
                        gson.fromJson("{\"id\":7100,\"levelRewardIndexId\":1}", BattlePassScheduleData.class));
        item =
                GameData.getItemDataMap()
                        .put(201, gson.fromJson("{\"id\":201,\"itemType\":\"ITEM_VIRTUAL\"}", ItemData.class));
        GameData.getBattlePassRewardDataMap()
                .put(
                        101,
                        gson.fromJson(
                                "{\"id\":101,\"freeRewardIdList\":[990001],\"paidRewardIdList\":[990002]}",
                                BattlePassRewardData.class));
        for (int id : List.of(990001, 990002))
            GameData.getRewardDataMap()
                    .put(
                            id,
                            gson.fromJson(
                                    "{\"id\":" + id + ",\"rewardItemList\":[{\"itemId\":201,\"itemCount\":60}]}",
                                    RewardData.class));
    }

    @AfterEach
    void restore() {
        GameData.getBattlePassMissionDataMap().clear();
        GameData.getBattlePassMissionDataMap().putAll(missions);
        GameData.getBattlePassScheduleDataMap().clear();
        GameData.getBattlePassScheduleDataMap().putAll(schedules);
        GameData.getBattlePassRewardDataMap().clear();
        GameData.getBattlePassRewardDataMap().putAll(levels);
        GameData.getRewardDataMap().clear();
        GameData.getRewardDataMap().putAll(rewards);
        if (item == null) GameData.getItemDataMap().remove(201);
        else GameData.getItemDataMap().put(201, item);
    }

    private BattlePassRewardTakeOption option(int id, boolean paid) {
        return BattlePassRewardTakeOption.newBuilder()
                .setTag(
                        BattlePassRewardTag.newBuilder()
                                .setLevel(1)
                                .setRewardId(id)
                                .setUnlockStatus(
                                        paid
                                                ? BattlePassUnlockStatus.BattlePassUnlockSTATUS_BATTLE_PASS_UNLOCK_PAID
                                                : BattlePassUnlockStatus.BattlePassUnlockSTATUS_BATTLE_PASS_UNLOCK_FREE))
                .build();
    }

    @Test
    void repeatedRewardInOneRequestAndLaterRequestsIsGrantedOnlyOnce() {
        var player = new FreeStoreTest.TestPlayer();
        player.pass.setLevel(1);
        var option = option(990001, false);
        player.pass.takeReward(List.of(option, option));
        player.pass.takeReward(List.of(option));
        assertEquals(60, player.granted.get(201));
    }

    @Test
    void forgedPaidTagOnFreeRewardCannotUnlockPremiumAccess() {
        var player = new FreeStoreTest.TestPlayer();
        player.pass.setLevel(1);
        player.pass.takeReward(List.of(option(990001, true), option(990002, true)));
        assertFalse(player.pass.isPaid());
        assertFalse(player.pass.getTakenRewards().containsKey(990002));
    }

    @Test
    void premiumRewardsRequireUnlockAndOnlySuccessfulOptionsAreReturned() {
        var player = new FreeStoreTest.TestPlayer();
        player.pass.setLevel(1);
        assertTrue(player.pass.takeReward(List.of(option(990002, true))).isEmpty());
        player.pass.unlockPaid(false);
        assertEquals(
                List.of(option(990002, true)),
                player.pass.takeReward(List.of(option(990002, true), option(999999, false))));
        assertEquals(60, player.granted.get(201));
    }

    @Test
    void invalidChestChoiceDoesNotLoseTheRewardAndValidChoiceIsGrantedOnce() {
        var prior =
                GameData.getItemDataMap()
                        .put(
                                990003,
                                gson.fromJson(
                                        "{\"id\":990003,\"itemType\":\"ITEM_MATERIAL\",\"materialType\":\"MATERIAL_SELECTABLE_CHEST\",\"itemUse\":[{\"useOp\":\"ITEM_USE_ADD_SELECT_ITEM\",\"useParam\":[\"201\"]}]}",
                                        ItemData.class));
        try {
            GameData.getRewardDataMap()
                    .put(
                            990001,
                            gson.fromJson(
                                    "{\"id\":990001,\"rewardItemList\":[{\"itemId\":990003,\"itemCount\":2}]}",
                                    RewardData.class));
            var player = new FreeStoreTest.TestPlayer();
            player.pass.setLevel(1);
            var invalid = option(990001, false);
            assertTrue(player.pass.takeReward(List.of(invalid)).isEmpty());
            assertTrue(player.pass.getTakenRewards().isEmpty());
            var valid = invalid.toBuilder().setOptionIdx(1).build();
            assertEquals(List.of(valid), player.pass.takeReward(List.of(valid, valid)));
            assertEquals(2, player.granted.get(201));
        } finally {
            if (prior == null) GameData.getItemDataMap().remove(990003);
            else GameData.getItemDataMap().put(990003, prior);
        }
    }

    @Test
    void dailyRefreshUsesShanghaiFourAmInsteadOfServerMidnight() throws Exception {
        var login =
                gson.fromJson(
                        "{\"id\":72001,\"progress\":1,\"addPoint\":120,\"refreshType\":\"BATTLE_PASS_MISSION_REFRESH_DAILY\",\"triggerConfig\":{\"triggerType\":\"TRIGGER_LOGIN\",\"paramList\":[]}}",
                        BattlePassMissionData.class);
        login.onLoad();
        GameData.getBattlePassMissionDataMap().put(login.getId(), login);
        var system = new BattlePassSystem(null);
        var player = new FreeStoreTest.TestPlayer();
        player.now = java.time.Instant.parse("2026-10-01T19:59:59Z");
        system.triggerMission(player, WatcherTriggerType.TRIGGER_LOGIN);
        player.pass.takeMissionPoint(List.of(72001));
        assertFalse(player.pass.refreshMissions());
        player.now = java.time.Instant.parse("2026-10-01T20:00:00Z");
        assertTrue(player.pass.refreshMissions());
        assertEquals(
                BattlePassMissionStatus.MISSION_STATUS_UNFINISHED,
                player.pass.loadMissionById(72001).getStatus());
        assertEquals(120, player.pass.getCyclePoints());
    }

    @Test
    void weeklyResetClearsWeeklyExperienceCap() {
        var player = new FreeStoreTest.TestPlayer();
        player.pass.addPointsDirectly(10000, true);
        assertEquals(10000, player.pass.getCyclePoints());
        player.pass.getMissions();
        player.pass.resetWeeklyMissions();
        assertEquals(0, player.pass.getCyclePoints());
    }

    @Test
    void resourceLoadedAfterSystemConstructionCanReloadDailyAndCurrentPeriodTasks() throws Exception {
        var system = new BattlePassSystem(null);
        var data =
                JsonUtils.loadToList(
                        FileUtils.getExcelPath("BattlePassMissionExcelConfigData.json"),
                        BattlePassMissionData.class);
        for (var mission : data) {
            mission.onLoad();
            GameData.getBattlePassMissionDataMap().put(mission.getId(), mission);
        }
        system.reload();
        var player = new FreeStoreTest.TestPlayer();
        system.triggerMission(player, WatcherTriggerType.TRIGGER_LOGIN);
        assertEquals(
                BattlePassMissionStatus.MISSION_STATUS_FINISHED,
                player.pass.loadMissionById(72001).getStatus());
        assertEquals(List.of(72001), player.pass.takeMissionPoint(List.of(72001, 72001)));
        assertEquals(120, player.pass.getPoint());
        assertEquals(120, player.pass.getCyclePoints());
        system.triggerMission(player, WatcherTriggerType.TRIGGER_GACHA_NUM, 0, 50);
        assertEquals(
                BattlePassMissionStatus.MISSION_STATUS_FINISHED,
                player.pass.loadMissionById(40211).getStatus());
        assertEquals(List.of(40211), player.pass.takeMissionPoint(List.of(40211)));
        assertEquals(120, player.pass.getCyclePoints(), "period missions are outside the weekly cap");
        assertEquals(1, player.pass.getLevel());
        assertTrue(player.pass.takeMissionPoint(List.of(72001, 73010)).isEmpty());
        system.triggerMission(player, "TRIGGER_CONSUME_RESIN", 0, 150);
        assertEquals(150, player.pass.loadMissionById(72004).getProgress());
        assertEquals(150, player.pass.loadMissionById(73006).getProgress());
        system.triggerMission(player, WatcherTriggerType.TRIGGER_DO_COOK, 0, 20);
        assertEquals(
                0,
                player.pass.loadMissionById(73010).getProgress(),
                "filtered tasks require the matching parameter");
        system.triggerMission(player, WatcherTriggerType.TRIGGER_DO_COOK, 1, 20);
        assertEquals(20, player.pass.loadMissionById(73010).getProgress());
        assertEquals(29, data.stream().filter(BattlePassMissionData::isValidRefreshType).count());
        assertFalse(GameData.getBattlePassMissionDataMap().get(73012).isValidRefreshType());
    }

    @Test
    void fourAmRefreshAndSkippedMondayResetOnlyRecurringTasks() throws Exception {
        var system = new BattlePassSystem(null);
        for (var data :
                JsonUtils.loadToList(
                        FileUtils.getExcelPath("BattlePassMissionExcelConfigData.json"),
                        BattlePassMissionData.class)) {
            data.onLoad();
            GameData.getBattlePassMissionDataMap().put(data.getId(), data);
        }
        system.reload();
        var player = new FreeStoreTest.TestPlayer();
        player.day = java.time.LocalDate.of(2026, 10, 4); // Sunday
        system.triggerMission(player, WatcherTriggerType.TRIGGER_LOGIN);
        system.triggerMission(player, WatcherTriggerType.TRIGGER_GACHA_NUM, 0, 10);
        system.triggerMission(player, WatcherTriggerType.TRIGGER_DO_COOK, 1, 10);
        player.pass.takeMissionPoint(List.of(72001));
        assertFalse(player.pass.refreshMissions());
        player.day = player.day.plusDays(2); // offline throughout Monday
        assertTrue(player.pass.refreshMissions());
        assertEquals(0, player.pass.getCyclePoints());
        assertEquals(0, player.pass.loadMissionById(72001).getProgress());
        assertEquals(0, player.pass.loadMissionById(73010).getProgress());
        assertEquals(10, player.pass.loadMissionById(40211).getProgress());
        assertEquals(120, player.pass.getPoint());
        var cycle = player.pass.getScheduleProto().getCurCycle();
        assertEquals(
                java.time.ZonedDateTime.parse("2026-10-05T04:00:00+08:00[Asia/Shanghai]").toEpochSecond(),
                cycle.getBeginTime());
        assertEquals(7 * 24 * 3600, cycle.getEndTime() - cycle.getBeginTime());
    }
}
