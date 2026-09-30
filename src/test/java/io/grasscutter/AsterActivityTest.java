package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.RewardData;
import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.net.proto.AsterLittleStageStateOuterClass.AsterLittleStageState;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class AsterActivityTest {
    private static final long START = Instant.parse("2026-10-01T02:00:00Z").toEpochMilli();
    private ActivityConfigItem config;
    private PlayerActivityData data;

    @BeforeAll
    static void bootstrap() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
    }

    @BeforeEach
    void resources() throws Exception {
        var gson = new Gson();
        var root = Path.of("resources/ExcelBinOutput");
        for (var r :
                gson.fromJson(
                        Files.readString(root.resolve("AsterLittleExcelConfigData.json")),
                        AsterLittleData[].class)) GameData.getAsterLittleDataMap().put(r.getId(), r);
        for (var r :
                gson.fromJson(
                        Files.readString(root.resolve("AsterMissionExcelConfigData.json")),
                        AsterMissionData[].class)) GameData.getAsterMissionDataMap().put(r.getId(), r);
        for (var r :
                gson.fromJson(
                        Files.readString(root.resolve("AsterStageExcelConfigData.json")),
                        AsterStageData[].class)) GameData.getAsterStageDataMap().put(r.getId(), r);
        for (var r :
                gson.fromJson(
                        Files.readString(root.resolve("AsterActivityPerviewExcelConfigData.json")),
                        AsterPreviewData[].class)) GameData.getAsterPreviewDataMap().put(r.getId(), r);
        for (var r :
                gson.fromJson(
                        Files.readString(root.resolve("RewardExcelConfigData.json")), RewardData[].class))
            if (r.getId() >= 3000001 && r.getId() <= 3000006) {
                r.onLoad();
                GameData.getRewardDataMap().put(r.getId(), r);
            }
        for (var r :
                gson.fromJson(
                        Files.readString(root.resolve("NewActivityWatcherConfigData.json")),
                        ActivityWatcherData[].class))
            if (r.getId() >= 1200101 && r.getId() <= 1200132) {
                r.onLoad();
                GameData.getActivityWatcherDataMap().put(r.getId(), r);
            }
        var activity =
                gson.fromJson(
                        "{activityId:2001,activityType:'NEW_ACTIVITY_ASTER',watcherId:[1200130,1200131,1200132],condGroupId:[2001001]}",
                        ActivityData.class);
        activity.onLoad();
        GameData.getActivityDataMap().put(2001, activity);
        config = new ActivityConfigItem();
        config.setActivityId(2001);
        config.setActivityType(1100);
        config.setScheduleId(2001009);
        config.setBeginTime(new Date(START));
        config.setEndTime(new Date(START + 30 * 86400000L));
        config.onLoad();
        var handler = new AsterActivityHandler();
        handler.setActivityConfigItem(config);
        handler.initWatchers(Map.of());
        data = handler.initPlayerActivityData(new emu.grasscutter.game.player.Player());
    }

    @AfterEach
    void cleanup() {
        GameData.getAsterLittleDataMap().clear();
        GameData.getAsterMissionDataMap().clear();
        GameData.getAsterStageDataMap().clear();
        GameData.getAsterPreviewDataMap().clear();
        GameData.getActivityDataMap().remove(2001);
        for (int i = 1200101; i <= 1200132; i++) GameData.getActivityWatcherDataMap().remove(i);
        for (int i = 3000001; i <= 3000006; i++) GameData.getRewardDataMap().remove(i);
    }

    @Test
    void stagesUseChinaFourAmRatherThanTwentyFourHoursAfterStart() {
        assertEquals(START, AsterSchedule.beginTime(config, 1));
        long day2 = Instant.parse("2026-10-01T20:00:00Z").toEpochMilli();
        assertEquals(day2, AsterSchedule.beginTime(config, 2));
        assertFalse(AsterSchedule.phaseOpen(data, config, 20, day2 - 1, 2));
        long day3 = day2 + 86400000L;
        assertFalse(AsterSchedule.phaseOpen(data, config, 20, day3 - 1, 2));
        assertTrue(AsterSchedule.phaseOpen(data, config, 20, day3, 2));
        assertFalse(AsterSchedule.phaseOpen(data, config, 19, day3, 2));
        assertFalse(AsterSchedule.phaseOpen(data, config, 20, START - 1, 1));
        assertFalse(
                AsterSchedule.phaseOpen(data, config, 20, AsterSchedule.contentCloseTime(config), 1));
    }

    @Test
    void allSixExplorationAndSixRecoveryWatchersComeFromMissions() {
        assertEquals(15, data.getWatcherInfoMap().size());
        assertEquals(Set.of(1200101, 1200102, 1200103), AsterSchedule.stageWatchers(1));
        assertEquals(Set.of(1200104, 1200105, 1200106), AsterSchedule.stageWatchers(2));
        assertFalse(
                data.getWatcherInfoMap().containsKey(1200107),
                "Disused test missions must not enter the replay");
        GameData.getAsterMissionDataMap().remove(3);
        assertThrows(
                IllegalArgumentException.class,
                () -> ActivityManager.prepareConfiguration(List.of(config)));
    }

    @Test
    void firstStageRequiresSevenFragmentsInEachOfThreeAreas() {
        collectStage(1, START);
        var info = AsterSchedule.littleInfo(data, config, 20, START);
        assertEquals(2, info.getStageId());
        assertEquals(
                AsterLittleStageState.AsterLittleStageState_ASTER_LITTLE_STAGE_UNSTARTED,
                info.getStageState());
        long day2 = AsterSchedule.beginTime(config, 2);
        assertEquals(
                AsterLittleStageState.AsterLittleStageState_ASTER_LITTLE_STAGE_STARTED,
                AsterSchedule.littleInfo(data, config, 20, day2).getStageState());
        collectStage(2, day2);
        assertEquals(
                AsterLittleStageState.AsterLittleStageState_ASTER_LITTLE_STAGE_FINISHED,
                AsterSchedule.littleInfo(data, config, 20, day2).getStageState());
        assertEquals(42, AsterSchedule.progress(data).credit());
    }

    private void collectStage(int stage, long now) {
        for (var area : AsterFragments.areas().values())
            if (area.stageId() == stage) {
                var points = area.configIds().stream().sorted().limit(7).toList();
                for (int id : points)
                    assertEquals(0, AsterGather.collect(data, config, 20, now, area.groupId(), id, () -> {}));
            }
    }

    @Test
    void collectionPersistsAndRejectsDuplicateUnknownAndFuturePoints() {
        var area = AsterFragments.areas().get(302001005);
        int id = area.configIds().stream().min(Integer::compare).orElseThrow();
        var saved = new AtomicInteger();
        assertEquals(
                0,
                AsterGather.collect(data, config, 20, START, area.groupId(), id, saved::incrementAndGet));
        var reloaded =
                PlayerActivityData.of()
                        .activityId(2001)
                        .scheduleId(config.getScheduleId())
                        .watcherInfoMap(data.getWatcherInfoMap())
                        .detail(data.getDetail())
                        .build();
        assertNotEquals(
                0,
                AsterGather.collect(
                        reloaded, config, 20, START, area.groupId(), id, saved::incrementAndGet));
        assertNotEquals(
                0,
                AsterGather.collect(
                        data, config, 20, START, area.groupId(), 999999, saved::incrementAndGet));
        var future = AsterFragments.areas().get(302001007);
        assertNotEquals(
                0,
                AsterGather.collect(
                        data,
                        config,
                        20,
                        START,
                        future.groupId(),
                        future.configIds().iterator().next(),
                        saved::incrementAndGet));
        assertEquals(1, saved.get());
        assertEquals(1, AsterSchedule.progress(reloaded).credit());
        config.setDisabled(true);
        assertNotEquals(
                0, AsterGather.collect(data, config, 20, START, area.groupId(), id + 1, () -> {}));
        config.setDisabled(false);
        assertEquals(1, AsterSchedule.progress(data).credit());
        data.setScheduleId(config.getScheduleId() + 1);
        assertFalse(AsterSchedule.phaseOpen(data, config, 20, START, 1));
    }

    @Test
    void failedAcknowledgedSaveRestoresCreditCollectionAndWatcherTogether() {
        String before = data.getDetail();
        var area = AsterFragments.areas().get(302001005);
        int id = area.configIds().iterator().next();
        assertThrows(
                IllegalStateException.class,
                () ->
                        AsterGather.collect(
                                data,
                                config,
                                20,
                                START,
                                area.groupId(),
                                id,
                                () -> {
                                    throw new IllegalStateException("DB unavailable");
                                }));
        assertEquals(before, data.getDetail());
        assertEquals(0, data.getWatcherInfoMap().get(1200101).getCurProgress());
        assertEquals(0, AsterGather.collect(data, config, 20, START, area.groupId(), id, () -> {}));
    }

    @Test
    void progressIsIsolatedBetweenPlayersAndFreshSchedules() {
        var other =
                PlayerActivityData.of()
                        .activityId(2001)
                        .scheduleId(config.getScheduleId())
                        .watcherInfoMap(new HashMap<>())
                        .detail("{}")
                        .build();
        collectStage(1, START);
        assertEquals(0, AsterSchedule.progress(other).credit());
        var handler = new AsterActivityHandler();
        handler.onInitPlayerActivityData(data);
        assertEquals(0, AsterSchedule.progress(data).credit());
    }

    @Test
    void allSixRegionRewardsUseOriginalDefinitionsAndRejectReplays() {
        collectStage(1, START);
        collectStage(2, AsterSchedule.beginTime(config, 2));
        var totals = new HashMap<Integer, Integer>();
        var saved = new AtomicInteger();
        for (int id = 1200101; id <= 1200106; id++) {
            int code =
                    AsterWatcherRewards.take(
                            data,
                            config,
                            20,
                            START + 86400000,
                            id,
                            reward -> 0,
                            reward ->
                                    GameData.getRewardDataMap()
                                            .get(reward)
                                            .getRewardItemList()
                                            .forEach(p -> totals.merge(p.getId(), p.getCount(), Integer::sum)),
                            saved::incrementAndGet);
            assertEquals(0, code);
            assertNotEquals(
                    0,
                    AsterWatcherRewards.take(
                            data,
                            config,
                            20,
                            START + 86400000,
                            id,
                            reward -> 0,
                            reward -> fail("duplicate grant"),
                            () -> {}));
        }
        assertEquals(Map.of(201, 180, 202, 120000), totals);
        assertEquals(12, saved.get());
    }

    @Test
    void rewardPreflightFailureAllowsRetryAndGrantFailureBlocksReplay() {
        collectStage(1, START);
        assertEquals(
                123,
                AsterWatcherRewards.take(
                        data, config, 20, START, 1200101, reward -> 123, reward -> fail(), () -> fail()));
        assertEquals(0, AsterSchedule.progress(data).pendingWatcherReward());
        assertThrows(
                IllegalStateException.class,
                () ->
                        AsterWatcherRewards.take(
                                data,
                                config,
                                20,
                                START,
                                1200101,
                                reward -> 0,
                                reward -> {
                                    throw new IllegalStateException("Partial inventory write");
                                },
                                () -> {}));
        assertEquals(1200101, AsterSchedule.progress(data).pendingWatcherReward());
        assertNotEquals(
                0,
                AsterWatcherRewards.take(
                        data, config, 20, START, 1200101, reward -> 0, reward -> fail(), () -> fail()));
    }

    @Test
    void rewardCompletionSaveFailureRetainsTheReservationAfterGrant() {
        collectStage(1, START);
        var saved = new AtomicInteger();
        var granted = new AtomicInteger();
        assertThrows(
                IllegalStateException.class,
                () ->
                        AsterWatcherRewards.take(
                                data,
                                config,
                                20,
                                START,
                                1200101,
                                reward -> 0,
                                reward -> granted.incrementAndGet(),
                                () -> {
                                    if (saved.incrementAndGet() == 2)
                                        throw new IllegalStateException("DB unavailable");
                                }));
        assertEquals(1, granted.get());
        assertFalse(data.getWatcherInfoMap().get(1200101).isTakenReward());
        assertEquals(1200101, AsterSchedule.progress(data).pendingWatcherReward());
        assertNotEquals(
                0,
                AsterWatcherRewards.take(
                        data, config, 20, START, 1200101, reward -> 0, reward -> fail(), () -> fail()));
    }

    @Test
    void existingGenericScheduleGetsMissingMissionsWithoutResettingPreviewProgress() {
        var handler = new AsterActivityHandler();
        handler.setActivityConfigItem(config);
        handler.initWatchers(Map.of());
        data.getWatcherInfoMap().keySet().removeIf(id -> id < 1200130);
        data.getWatcherInfoMap().get(1200130).setCurProgress(1);
        assertTrue(handler.onLoadPlayerActivityData(data));
        assertEquals(15, data.getWatcherInfoMap().size());
        assertEquals(1, data.getWatcherInfoMap().get(1200130).getCurProgress());
        assertFalse(handler.onLoadPlayerActivityData(data));
    }
}
