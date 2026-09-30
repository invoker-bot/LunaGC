package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.RewardData;
import emu.grasscutter.data.excels.activity.SalesmanData;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.salesman.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;

class SalesmanRewardsTest {
    private static final long NOW = Instant.parse("2026-10-01T12:00:00Z").toEpochMilli();
    private ActivityConfigItem config;
    private PlayerActivityData data;
    @BeforeEach void setup() throws Exception {
        var gson = new Gson(); var root = Path.of("resources/ExcelBinOutput");
        for (var row : gson.fromJson(Files.readString(root.resolve("ActivitySalesmanExcelConfigData.json")), SalesmanData[].class))
            GameData.getSalesmanDataMap().put(row.getId(), row);
        for (var row : gson.fromJson(Files.readString(root.resolve("RewardExcelConfigData.json")), RewardData[].class))
            if (row.getId() >= 470001 && row.getId() <= 470007) { row.onLoad(); GameData.getRewardDataMap().put(row.getId(), row); }
        config = new ActivityConfigItem(); config.setActivityId(5003); config.setActivityType(3); config.setScheduleId(5003010);
        config.setBeginTime(new Date(NOW - 1000)); config.setEndTime(new Date(NOW + 8 * 86400000L)); config.onLoad();
        data = PlayerActivityData.of().activityId(5003).scheduleId(5003010).build();
        var progress = new SalesmanProgress(); for (int day = 1; day <= 7; day++) progress.deliver(day); data.setDetail(progress);
    }
    @AfterEach void cleanup() {
        GameData.getSalesmanDataMap().clear();
        for (int id = 470001; id <= 470007; id++) GameData.getRewardDataMap().remove(id);
    }
    private SalesmanRewards.Result take(int position, double draw, int index, java.util.function.IntConsumer grant, Runnable save) {
        return SalesmanRewards.take(data, config, 12, NOW, position, () -> draw, bound -> index, ignored -> 0, grant, save);
    }
    @Test void sevenChoicesHaveNoDuplicateRewardsAndTheLastUnobtainedSpecialIsGuaranteed() {
        var ids = new HashSet<Integer>();
        for (int position : List.of(7, 1, 5, 2, 6, 3, 4)) {
            var result = take(position, 0.99, 0, reward -> assertTrue(ids.add(reward)), () -> {});
            assertEquals(0, result.retcode());
            assertEquals(ids.size() == 7 ? 470007 : 470000 + ids.size(), result.rewardId());
        }
        assertEquals(Set.of(470001,470002,470003,470004,470005,470006,470007), ids);
        assertEquals(0, SalesmanSchedule.progress(data).remainingChances());
        assertEquals(7, SalesmanActivityHandler.detail(data, config, NOW).getSelectedRewardIdMapCount());
    }
    @Test void specialCanAppearEarlyAndIsNeverRolledAgainAfterItWasObtained() {
        assertEquals(470007, take(2, 0, 0, ignored -> {}, () -> {}).rewardId());
        var result = SalesmanRewards.take(data, config, 12, NOW, 4,
                () -> { fail("Special already obtained"); return 0; }, bound -> { assertEquals(6, bound); return 5; },
                ignored -> 0, ignored -> {}, () -> {});
        assertEquals(0, result.retcode()); assertEquals(470006, result.rewardId());
    }
    @Test void savedReservationPrecedesGrantAndFinalizedProgressSurvivesReload() {
        var events = new ArrayList<String>(); var persisted = new AtomicReference<String>();
        var result = take(5, 0.99, 2, reward -> {
            assertNotNull(persisted.get()); assertTrue(persisted.get().contains("pendingRewardPosition"));
            var inFlight = PlayerActivityData.of().activityId(5003).scheduleId(5003010).detail(persisted.get()).build();
            assertEquals(Retcode.RET_SVR_ERROR_VALUE, SalesmanRewards.take(inFlight, config, 12, NOW, 1,
                    () -> 0, bound -> 0, ignored -> { fail("Reserved claim validation"); return 0; }, ignored -> fail("Reserved grant"), () -> fail("Reserved save")).retcode());
            events.add("grant");
        }, () -> { persisted.set(data.getDetail()); events.add("save"); });
        assertEquals(0, result.retcode()); assertEquals(470003, result.rewardId());
        assertEquals(List.of("save", "grant", "save"), events);
        data = PlayerActivityData.of().activityId(5003).scheduleId(5003010).detail(persisted.get()).build();
        assertEquals(470003, SalesmanSchedule.progress(data).selectedRewardIdMap().get(5));
        assertEquals(Retcode.RET_SALESMAN_POSITION_INVALID_VALUE, takeWithoutMutation(5, 12, NOW).retcode());
    }
    private SalesmanRewards.Result takeWithoutMutation(int position, int rank, long now) {
        return SalesmanRewards.take(data, config, rank, now, position, () -> { fail("Unexpected random draw"); return 0; },
                bound -> { fail("Unexpected random index"); return 0; }, ignored -> { fail("Unexpected validation"); return 0; },
                ignored -> fail("Unexpected grant"), () -> fail("Unexpected save"));
    }
    @Test void invalidClosedStaleLowRankAndExhaustedClaimsDoNotTouchInventory() {
        for (int position : List.of(0, 8, -1, Integer.MIN_VALUE))
            assertEquals(Retcode.RET_SALESMAN_POSITION_INVALID_VALUE, takeWithoutMutation(position, 12, NOW).retcode());
        config.setDisabled(true); assertEquals(Retcode.RET_ACTIVITY_CLOSE_VALUE, takeWithoutMutation(1, 12, NOW).retcode());
        config.setDisabled(false); data.setScheduleId(5003009);
        assertEquals(Retcode.RET_ACTIVITY_CLOSE_VALUE, takeWithoutMutation(1, 12, NOW).retcode());
        data.setScheduleId(5003010); assertEquals(Retcode.RET_PLAYER_LEVEL_LESS_THAN_VALUE, takeWithoutMutation(1, 11, NOW).retcode());
        assertEquals(Retcode.RET_ACTIVITY_CLOSE_VALUE, takeWithoutMutation(1, 12, NOW + 8 * 86400000L).retcode());
        data.setDetail(new SalesmanProgress());
        assertEquals(Retcode.RET_SALESMAN_REWARD_COUNT_NOT_ENOUGH_VALUE, takeWithoutMutation(1, 12, NOW).retcode());
    }
    @Test void inventoryFullDoesNotReserveOrConsumeAChance() {
        String before = data.getDetail();
        var result = SalesmanRewards.take(data, config, 12, NOW, 1, () -> 0.99, bound -> 0,
                ignored -> Retcode.RET_ITEM_EXCEED_LIMIT_VALUE, ignored -> fail("Full inventory grant"), () -> fail("Full inventory save"));
        assertEquals(Retcode.RET_ITEM_EXCEED_LIMIT_VALUE, result.retcode()); assertEquals(0, result.rewardId());
        assertEquals(before, data.getDetail());
    }
    @Test void exceptionsDuringReservationGrantAndFinalSaveNeverReplayAnUnknownGrant() {
        for (int failure : List.of(1, 2, 3)) {
            var progress = new SalesmanProgress(); progress.deliver(1); data.setDetail(progress);
            var persisted = new AtomicReference<String>(); var writes = new AtomicInteger(); var grants = new AtomicInteger();
            assertThrows(IllegalStateException.class, () -> take(1, 0.99, 0, ignored -> {
                grants.incrementAndGet(); if (failure == 2) throw new IllegalStateException("Interrupted grant");
            }, () -> {
                int count = writes.incrementAndGet();
                if (failure == 1 || (failure == 3 && count == 2)) throw new IllegalStateException("Interrupted save");
                persisted.set(data.getDetail());
            }));
            assertEquals(failure == 1 ? 0 : 1, grants.get());
            assertEquals(Retcode.RET_SVR_ERROR_VALUE, takeWithoutMutation(2, 12, NOW).retcode());
            if (persisted.get() != null) {
                data = PlayerActivityData.of().activityId(5003).scheduleId(5003010).detail(persisted.get()).build();
                assertEquals(Retcode.RET_SVR_ERROR_VALUE, takeWithoutMutation(2, 12, NOW).retcode());
            }
        }
    }
    @Test void concurrentAndReentrantRequestsGrantExactlyOnce() throws Exception {
        var grants = new AtomicInteger(); var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> attempt = () -> { start.await(); return take(3, 0.99, 0, ignored -> {
                assertEquals(Retcode.RET_SVR_ERROR_VALUE, takeWithoutMutation(4, 12, NOW).retcode()); grants.incrementAndGet();
            }, () -> {}).retcode(); };
            var one = pool.submit(attempt); var two = pool.submit(attempt); start.countDown();
            assertEquals(Set.of(0, Retcode.RET_SALESMAN_POSITION_INVALID_VALUE), Set.of(one.get(5, TimeUnit.SECONDS), two.get(5, TimeUnit.SECONDS)));
            assertEquals(1, grants.get());
        } finally { pool.shutdownNow(); }
    }
    @Test void malformedProgressAndResourcesAreRejectedBeforeAnyGrant() {
        for (String json : List.of("{\"selectedRewardIdMap\":{\"0\":470001},\"deliveredDays\":[1]}",
                "{\"pendingRewardPosition\":1}", "{\"pendingRewardPosition\":1,\"pendingRewardId\":470009}")) {
            data.setDetailJson(json); assertEquals(Retcode.RET_SVR_ERROR_VALUE, takeWithoutMutation(1, 12, NOW).retcode());
        }
        var progress = new SalesmanProgress(); progress.deliver(1); data.setDetail(progress);
        GameData.getRewardDataMap().remove(470007);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, takeWithoutMutation(1, 12, NOW).retcode());
    }
    @Test void specialProbabilityUsesTheResourceThresholdAndInvalidDrawsNeverReserve() {
        double threshold = SalesmanSchedule.source().getSpecialProbList().get(0);
        assertEquals(470007, take(1, Math.nextDown(threshold), 0, ignored -> {}, () -> {}).rewardId());
        setupProgress();
        assertEquals(470001, take(1, threshold, 0, ignored -> {}, () -> {}).rewardId());
        setupProgress(); String before = data.getDetail();
        for (double draw : List.of(-1.0, 1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(Retcode.RET_SVR_ERROR_VALUE, take(1, draw, 0, ignored -> fail("Invalid draw grant"), () -> fail("Invalid draw save")).retcode());
            assertEquals(before, data.getDetail());
        }
        for (int index : List.of(-1, 6)) assertEquals(Retcode.RET_SVR_ERROR_VALUE,
                take(1, 0.99, index, ignored -> fail("Invalid index grant"), () -> fail("Invalid index save")).retcode());
    }
    private void setupProgress() { var progress = new SalesmanProgress(); progress.deliver(1); data.setDetail(progress); }
    @Test void unusedChancesCanBeTakenDuringAnExtensionAndBadProbabilityTablesAreRejected() {
        var result = SalesmanRewards.take(data, config, 12, NOW + 7 * 86400000L, 7, () -> 0.99, bound -> 0,
                ignored -> 0, ignored -> {}, () -> {});
        assertEquals(0, result.retcode());
        var gson = new Gson(); var json = gson.toJsonTree(SalesmanSchedule.source()).getAsJsonObject();
        var probabilities = new com.google.gson.JsonArray();
        for (int index = 0; index < 7; index++) probabilities.add(index == 6 ? 0.5 : 0.14);
        json.add("specialProbList", probabilities);
        GameData.getSalesmanDataMap().put(5003001, gson.fromJson(json, SalesmanData.class));
        assertFalse(SalesmanRewards.resourcesAvailable(SalesmanSchedule.source()));
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, takeWithoutMutation(1, 12, NOW).retcode());
    }
}
