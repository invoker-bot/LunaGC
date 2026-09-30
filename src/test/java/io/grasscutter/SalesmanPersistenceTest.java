package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import dev.morphia.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.salesman.*;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.net.packet.BasePacket;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;

/** Exercises the real inventory and persistence entry points with an isolated datastore double. */
class SalesmanPersistenceTest {
    private Field datastoreField;
    private Object original;
    @BeforeEach void prepare() throws Exception {
        // Match server bootstrap order: Configuration's constants depend on Grasscutter.config.
        Class.forName("emu.grasscutter.Grasscutter");
        datastoreField = DatabaseManager.class.getDeclaredField("gameDatastore"); datastoreField.setAccessible(true);
        original = datastoreField.get(null);
    }
    private void store(InvocationHandler handler) throws Exception {
        datastoreField.set(null, Proxy.newProxyInstance(Datastore.class.getClassLoader(), new Class<?>[]{Datastore.class}, handler));
    }
    private static void drain() throws Exception {
        var executor = (ThreadPoolExecutor) DatabaseHelper.getEventExecutor();
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (executor.getActiveCount() != 0 || !executor.getQueue().isEmpty()) {
            if (System.nanoTime() > until) fail("Database test jobs did not finish");
            Thread.sleep(5);
        }
    }
    @AfterEach void restore() throws Exception { try { drain(); } finally { datastoreField.set(null, original); } }
    @Test void reservationSaveUsesJournalAcknowledgementAndPropagatesErrorsToTheCaller() throws Exception {
        var thread = Thread.currentThread(); var called = new AtomicBoolean();
        store((proxy, method, args) -> {
            assertEquals("save", method.getName()); assertSame(thread, Thread.currentThread());
            var options = (InsertOneOptions) args[1]; assertEquals("majority", options.writeConcern().getWString());
            assertEquals(Boolean.TRUE, options.writeConcern().getJournal()); called.set(true);
            throw new IllegalStateException("Unavailable DB");
        });
        var data = PlayerActivityData.of().activityId(5003).scheduleId(5003010).detail("{}").build();
        assertThrows(IllegalStateException.class, data::saveSync);
        assertThrows(IllegalStateException.class, data::save); assertTrue(called.get());
    }
    @Test void inFlightAsyncWriteFinishesBeforeAnAcknowledgedNewerWrite() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
        var calls = new AtomicInteger(); var durable = new AtomicInteger(); var item = new GameItem(); item.setCount(1);
        store((proxy, method, args) -> {
            int snapshot = item.getCount();
            if (calls.incrementAndGet() == 1) { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); }
            durable.set(snapshot); return args[0];
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            DatabaseHelper.saveGameAsync(item); assertTrue(entered.await(5, TimeUnit.SECONDS)); item.setCount(2);
            var newer = executor.submit(() -> { started.countDown(); DatabaseHelper.saveGameSync(item); });
            assertTrue(started.await(5, TimeUnit.SECONDS)); assertFalse(newer.isDone());
            release.countDown(); newer.get(5, TimeUnit.SECONDS); drain(); assertEquals(2, durable.get());
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    @Test void queuedSavesOfARemovedStackDeleteItWithoutRecreatingTheDocument() throws Exception {
        var deleted = new CountDownLatch(1); var calls = new AtomicInteger(); var item = new GameItem(); item.setCount(10);
        store((proxy, method, args) -> {
            assertEquals("delete", method.getName()); calls.incrementAndGet(); deleted.countDown(); return null;
        });
        synchronized (item) { DatabaseHelper.saveGameAsync(item); item.setCount(0); }
        assertTrue(deleted.await(5, TimeUnit.SECONDS)); drain(); DatabaseHelper.saveGameSync(item);
        assertEquals(2, calls.get());
    }
    private static final class TestPlayer extends Player {
        @Override public int getUid() { return 12345; }
        @Override public void sendPacket(BasePacket packet) {}
        @Override public boolean setProperty(PlayerProperty property, int value) {
            getProperties().put(property.getId(), value); return true;
        }
    }
    @Test void sevenDayExchangeGrantsActualInventoryAndPersistsAllSevenBoxSelections() throws Exception {
        var gson = new Gson(); var root = Path.of("resources/ExcelBinOutput"); var loaded = new HashSet<Integer>();
        var savedDetail = new AtomicReference<String>(); var savedPrimogems = new AtomicInteger();
        store((proxy, method, args) -> {
            if (method.getName().equals("save")) {
                if (args[0] instanceof PlayerActivityData data) savedDetail.set(data.getDetail());
                if (args[0] instanceof Player player) savedPrimogems.set(player.getPrimogems());
                return args[0];
            }
            return null;
        });
        try {
            for (var row : gson.fromJson(Files.readString(root.resolve("ActivitySalesmanExcelConfigData.json")), SalesmanData[].class))
                GameData.getSalesmanDataMap().put(row.getId(), row);
            for (var row : gson.fromJson(Files.readString(root.resolve("ActivitySalesmanDailyExcelConfigData.json")), SalesmanDailyData[].class)) {
                row.onLoad(); GameData.getSalesmanDailyDataMap().put(row.getId(), row);
            }
            for (var row : gson.fromJson(Files.readString(root.resolve("RewardExcelConfigData.json")), RewardData[].class))
                if (row.getId() >= 470001 && row.getId() <= 470007) { row.onLoad(); GameData.getRewardDataMap().put(row.getId(), row); }
            var needed = new HashSet<Integer>();
            for (int day = 1; day <= 7; day++) for (var param : GameData.getSalesmanDailyDataMap().get(day).getCostItemList()) needed.add(param.getId());
            var totals = new HashMap<Integer, Integer>();
            for (int id = 470001; id <= 470007; id++) for (var param : GameData.getRewardDataMap().get(id).getRewardItemList()) {
                needed.add(param.getId()); totals.merge(param.getId(), param.getCount(), Integer::sum);
            }
            for (var row : gson.fromJson(Files.readString(root.resolve("MaterialExcelConfigData.json")), ItemData[].class))
                if (needed.contains(row.getId())) { loaded.add(row.getId()); GameData.getItemDataMap().put(row.getId(), row); }
            var player = new TestPlayer(); long now = Instant.parse("2026-10-01T12:00:00Z").toEpochMilli();
            var config = new ActivityConfigItem(); config.setActivityId(5003); config.setActivityType(3); config.setScheduleId(5003010);
            config.setBeginTime(new Date(now - 1000)); config.setEndTime(new Date(now + 8 * 86400000L)); config.onLoad();
            var data = PlayerActivityData.of().activityId(5003).scheduleId(5003010).detail("{}").build();
            for (int day = 1; day <= 7; day++) {
                long current = now + (day - 1) * 86400000L;
                for (var cost : SalesmanSchedule.daily(config, current).getCostItemList()) assertTrue(player.getInventory().addItem(cost));
                assertEquals(0, SalesmanTalk.complete(data, config, 4100100 + day, 12, current, data::saveSync));
                assertEquals(0, SalesmanDelivery.deliver(data, config, 12, current, costs -> SalesmanRewardDelivery.pay(player, costs), data::saveSync));
                synchronized (player.getInventory()) {
                    var result = SalesmanRewards.take(data, config, 12, current, 8 - day, () -> 0.99, bound -> 0,
                            reward -> SalesmanRewardDelivery.validate(player, reward), reward -> SalesmanRewardDelivery.grant(player, reward), data::saveSync);
                    assertEquals(0, result.retcode());
                }
            }
            drain(); assertEquals(210, player.getPrimogems()); assertEquals(210, savedPrimogems.get());
            assertEquals(totals.get(202).intValue(), player.getMora());
            for (var total : totals.entrySet()) if (total.getKey() != 201 && total.getKey() != 202)
                assertEquals(total.getValue().intValue(), player.getInventory().getItemCountById(total.getKey()));
            var reloaded = PlayerActivityData.of().activityId(5003).scheduleId(5003010).detail(savedDetail.get()).build();
            assertEquals(7, SalesmanSchedule.progress(reloaded).selectedRewardIdMap().size());
            assertEquals(0, SalesmanSchedule.progress(reloaded).remainingChances());
        } finally {
            drain(); for (int id : loaded) GameData.getItemDataMap().remove(id);
            GameData.getSalesmanDataMap().clear(); GameData.getSalesmanDailyDataMap().clear();
            for (int id = 470001; id <= 470007; id++) GameData.getRewardDataMap().remove(id);
        }
    }
}
