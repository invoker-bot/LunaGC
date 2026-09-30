package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.salesman.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;

class SalesmanDeliveryTest {
    private static final long NOW = Instant.parse("2026-09-30T12:00:00Z").toEpochMilli();
    private ActivityConfigItem config;
    private PlayerActivityData data;
    @BeforeEach void setup() throws Exception {
        var gson = new Gson(); var root = Path.of("resources/ExcelBinOutput");
        for (var row : gson.fromJson(Files.readString(root.resolve("ActivitySalesmanExcelConfigData.json")), SalesmanData[].class))
            GameData.getSalesmanDataMap().put(row.getId(), row);
        for (var row : gson.fromJson(Files.readString(root.resolve("ActivitySalesmanDailyExcelConfigData.json")), SalesmanDailyData[].class)) {
            row.onLoad(); GameData.getSalesmanDailyDataMap().put(row.getId(), row);
        }
        config = new ActivityConfigItem(); config.setActivityId(5003); config.setActivityType(3); config.setScheduleId(5003009);
        config.setBeginTime(new Date(NOW - 1000)); config.setEndTime(new Date(NOW + 8 * 86400000L)); config.onLoad();
        data = PlayerActivityData.of().activityId(5003).scheduleId(5003009).build(); data.setDetail(new SalesmanProgress());
    }
    @AfterEach void cleanup() { GameData.getSalesmanDataMap().clear(); GameData.getSalesmanDailyDataMap().clear(); }
    @Test void onePaymentCreatesOneChanceAndReloadRejectsDuplicates() {
        var payments = new AtomicInteger(); var persisted = new AtomicReference<String>();
        assertEquals(0, SalesmanDelivery.deliver(data, config, 12, NOW, costs -> {
            assertEquals(1, costs.size()); assertEquals(100011, costs.get(0).getId()); assertEquals(10, costs.get(0).getCount());
            payments.incrementAndGet(); return true;
        }, () -> persisted.set(data.getDetail())));
        assertEquals(1, SalesmanSchedule.progress(data).remainingChances());
        var reloaded = PlayerActivityData.of().activityId(5003).scheduleId(5003009).detail(persisted.get()).build();
        assertEquals(Retcode.RET_SALESMAN_ALREADY_DELIVERED_VALUE,
            SalesmanDelivery.deliver(reloaded, config, 12, NOW, costs -> { fail("Duplicate debit"); return false; }, () -> fail("Duplicate save")));
        assertEquals(1, payments.get());
    }
    @Test void insufficientMaterialsCanBeRetriedAndNextDayHasItsOwnCost() {
        var writes = new AtomicInteger();
        assertEquals(Retcode.RET_ITEM_COUNT_NOT_ENOUGH_VALUE,
            SalesmanDelivery.deliver(data, config, 12, NOW, costs -> false, writes::incrementAndGet));
        assertEquals(0, SalesmanSchedule.progress(data).remainingChances());
        assertEquals(0, SalesmanDelivery.deliver(data, config, 12, NOW, costs -> true, writes::incrementAndGet));
        long tomorrow = Instant.parse("2026-09-30T20:00:00Z").toEpochMilli();
        assertEquals(0, SalesmanDelivery.deliver(data, config, 12, tomorrow, costs -> {
            assertEquals(100020, costs.get(0).getId()); return true;
        }, writes::incrementAndGet));
        assertEquals(2, SalesmanSchedule.progress(data).remainingChances());
    }
    @Test void closedStaleLowRankAndMissingResourcesNeverDebit() {
        config.setDisabled(true);
        assertEquals(Retcode.RET_ACTIVITY_CLOSE_VALUE, attemptWithoutMutation(12, NOW));
        config.setDisabled(false); data.setScheduleId(5003010);
        assertEquals(Retcode.RET_ACTIVITY_CLOSE_VALUE, attemptWithoutMutation(12, NOW));
        data.setScheduleId(5003009);
        assertEquals(Retcode.RET_PLAYER_LEVEL_LESS_THAN_VALUE, attemptWithoutMutation(11, NOW));
        assertEquals(Retcode.RET_ACTIVITY_CLOSE_VALUE, attemptWithoutMutation(12, NOW + 8 * 86400000L));
        GameData.getSalesmanDailyDataMap().remove(1);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, attemptWithoutMutation(12, NOW));
    }
    private int attemptWithoutMutation(int rank, long now) {
        return SalesmanDelivery.deliver(data, config, rank, now, costs -> { fail("Unexpected debit"); return false; }, () -> fail("Unexpected save"));
    }
    @Test void simultaneousRequestsChargeOnce() throws Exception {
        var payments = new AtomicInteger(); var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> attempt = () -> { start.await(); return SalesmanDelivery.deliver(data, config, 12, NOW,
                    costs -> { payments.incrementAndGet(); return true; }, () -> {}); };
            var one = pool.submit(attempt); var two = pool.submit(attempt); start.countDown();
            assertEquals(Set.of(0, Retcode.RET_SALESMAN_ALREADY_DELIVERED_VALUE), Set.of(one.get(5, TimeUnit.SECONDS), two.get(5, TimeUnit.SECONDS)));
            assertEquals(1, payments.get());
        } finally { pool.shutdownNow(); }
    }
    @Test void failedPaymentWithUnknownOutcomeCannotBeReplayedAfterReload() {
        var persisted = new AtomicReference<String>();
        assertThrows(IllegalStateException.class, () -> SalesmanDelivery.deliver(data, config, 12, NOW,
                costs -> { throw new IllegalStateException("Payment interrupted"); }, () -> persisted.set(data.getDetail())));
        var reloaded = PlayerActivityData.of().activityId(5003).scheduleId(5003009).detail(persisted.get()).build();
        assertEquals(0, SalesmanSchedule.progress(reloaded).remainingChances());
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, SalesmanDelivery.deliver(reloaded, config, 12, NOW,
                costs -> { fail("Unknown payment replay"); return false; }, () -> fail("Unknown payment replay save")));
    }
    @Test void persistenceFailurePreventsDebitAndReentrantCallbacksCannotDoubleCharge() {
        assertThrows(IllegalStateException.class, () -> SalesmanDelivery.deliver(data, config, 12, NOW,
                costs -> { fail("Debit before saved reservation"); return false; }, () -> { throw new IllegalStateException("DB unavailable"); }));
        data.setDetail(new SalesmanProgress());
        assertEquals(0, SalesmanDelivery.deliver(data, config, 12, NOW, costs -> {
            assertEquals(Retcode.RET_SVR_ERROR_VALUE, attemptWithoutMutation(12, NOW)); return true;
        }, () -> {}));
    }
    @Test void interruptedFinalSaveRetainsReservationInDatabaseWithoutAnotherCharge() {
        var writes = new AtomicInteger(); var persisted = new AtomicReference<String>(); var payments = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> SalesmanDelivery.deliver(data, config, 12, NOW,
                costs -> { payments.incrementAndGet(); return true; }, () -> {
                    if (writes.incrementAndGet() == 1) persisted.set(data.getDetail());
                    else throw new IllegalStateException("Final write interrupted");
                }));
        assertEquals(Retcode.RET_SALESMAN_ALREADY_DELIVERED_VALUE, attemptWithoutMutation(12, NOW));
        var reloaded = PlayerActivityData.of().activityId(5003).scheduleId(5003009).detail(persisted.get()).build();
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, SalesmanDelivery.deliver(reloaded, config, 12, NOW,
                costs -> { fail("Paid delivery must not debit again after restart"); return false; }, () -> {}));
        assertEquals(1, payments.get());
    }
    @Test void malformedPersistedProgressFailsWithoutMutation() {
        data = PlayerActivityData.of().activityId(5003).scheduleId(5003009).detail("{\"pendingDeliveryDay\":8}").build();
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, attemptWithoutMutation(12, NOW));
        assertEquals("{\"pendingDeliveryDay\":8}", data.getDetail());
    }
}
