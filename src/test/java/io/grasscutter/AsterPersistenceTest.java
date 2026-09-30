package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import dev.morphia.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.game.activity.salesman.SalesmanRewardDelivery;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.BasePacket;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;

/** Real virtual-currency writes and acknowledged activity saves, without a production database. */
class AsterPersistenceTest {
    private Field datastoreField;
    private Object original;
    private ActivityConfigItem config;
    private PlayerActivityData data;
    private TestPlayer player;
    private final Map<Integer, ItemData> previousItems = new HashMap<>();

    private static final class TestPlayer extends Player {
        @Override
        public int getUid() {
            return 12345;
        }

        @Override
        public void sendPacket(BasePacket packet) {}

        @Override
        public boolean setProperty(PlayerProperty property, int value) {
            getProperties().put(property.getId(), value);
            return true;
        }
    }

    private void store(InvocationHandler handler) throws Exception {
        datastoreField.set(
                null,
                Proxy.newProxyInstance(
                        Datastore.class.getClassLoader(), new Class<?>[] {Datastore.class}, handler));
    }

    private static void drain() throws Exception {
        var executor = (ThreadPoolExecutor) DatabaseHelper.getEventExecutor();
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (executor.getActiveCount() != 0 || !executor.getQueue().isEmpty()) {
            if (System.nanoTime() > until) fail("Database test jobs did not finish");
            Thread.sleep(5);
        }
    }

    @BeforeEach
    void prepare() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        datastoreField = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastoreField.setAccessible(true);
        original = datastoreField.get(null);
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
        var watchers = new HashMap<Integer, PlayerActivityData.WatcherInfo>();
        for (var r :
                gson.fromJson(
                        Files.readString(root.resolve("NewActivityWatcherConfigData.json")),
                        ActivityWatcherData[].class))
            if (r.getId() >= 1200101 && r.getId() <= 1200106) {
                r.onLoad();
                GameData.getActivityWatcherDataMap().put(r.getId(), r);
                watchers.put(
                        r.getId(),
                        PlayerActivityData.WatcherInfo.of()
                                .watcherId(r.getId())
                                .totalProgress(r.getProgress())
                                .build());
            }
        for (var r :
                gson.fromJson(
                        Files.readString(root.resolve("RewardExcelConfigData.json")), RewardData[].class))
            if (r.getId() >= 3000001 && r.getId() <= 3000006) {
                r.onLoad();
                GameData.getRewardDataMap().put(r.getId(), r);
            }
        for (var r :
                gson.fromJson(
                        Files.readString(root.resolve("MaterialExcelConfigData.json")), ItemData[].class))
            if (r.getId() == 201 || r.getId() == 202) {
                previousItems.put(r.getId(), GameData.getItemDataMap().put(r.getId(), r));
            }
        long start = System.currentTimeMillis() - 3 * 86400000L;
        config = new ActivityConfigItem();
        config.setActivityId(2001);
        config.setActivityType(1100);
        config.setScheduleId(2001099);
        config.setBeginTime(new Date(start));
        config.setEndTime(new Date(start + 30 * 86400000L));
        config.onLoad();
        player = new TestPlayer();
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 20);
        data =
                PlayerActivityData.of()
                        .uid(player.getUid())
                        .activityId(2001)
                        .scheduleId(config.getScheduleId())
                        .watcherInfoMap(watchers)
                        .detail("{}")
                        .player(player)
                        .build();
    }

    @AfterEach
    void restore() throws Exception {
        try {
            drain();
        } finally {
            datastoreField.set(null, original);
            GameData.getAsterLittleDataMap().clear();
            GameData.getAsterMissionDataMap().clear();
            GameData.getAsterStageDataMap().clear();
            GameData.getAsterPreviewDataMap().clear();
            for (int id = 1200101; id <= 1200106; id++) GameData.getActivityWatcherDataMap().remove(id);
            for (int id = 3000001; id <= 3000006; id++) GameData.getRewardDataMap().remove(id);
            previousItems.forEach(
                    (id, value) -> {
                        if (value == null) GameData.getItemDataMap().remove(id);
                        else GameData.getItemDataMap().put(id, value);
                    });
        }
    }

    @Test
    void collectedProgressAndAllSixRewardsSurviveAcknowledgedSaves() throws Exception {
        var savedDetail = new AtomicReference<String>();
        var savedWatchers = new AtomicReference<String>();
        var savedPrimogems = new AtomicInteger();
        var savedMora = new AtomicInteger();
        var gson = new Gson();
        store(
                (proxy, method, args) -> {
                    assertEquals("save", method.getName());
                    if (args[0] instanceof PlayerActivityData saved) {
                        var options = (InsertOneOptions) args[1];
                        assertEquals("majority", options.writeConcern().getWString());
                        assertEquals(Boolean.TRUE, options.writeConcern().getJournal());
                        savedDetail.set(saved.getDetail());
                        savedWatchers.set(gson.toJson(saved.getWatcherInfoMap()));
                    }
                    if (args[0] instanceof Player saved) {
                        savedPrimogems.set(saved.getPrimogems());
                        savedMora.set(saved.getMora());
                    }
                    return args[0];
                });
        long now = System.currentTimeMillis();
        for (var area :
                AsterFragments.areas().values().stream()
                        .sorted(Comparator.comparingInt(AsterFragments.Area::stageId))
                        .toList()) {
            for (int id : area.configIds().stream().sorted().limit(7).toList())
                assertEquals(
                        0, AsterGather.collect(data, config, 20, now, area.groupId(), id, data::saveSync));
            synchronized (player.getInventory()) {
                assertEquals(
                        0,
                        AsterWatcherRewards.take(
                                data,
                                config,
                                20,
                                now,
                                area.watcherId(),
                                reward -> SalesmanRewardDelivery.validate(player, reward),
                                reward ->
                                        SalesmanRewardDelivery.grant(player, reward, ActionReason.ActivityWatcher),
                                data::saveSync));
            }
        }
        drain();
        assertEquals(180, player.getPrimogems());
        assertEquals(120000, player.getMora());
        assertEquals(180, savedPrimogems.get());
        assertEquals(120000, savedMora.get());
        var mapType =
                new com.google.gson.reflect.TypeToken<
                        Map<Integer, PlayerActivityData.WatcherInfo>>() {}.getType();
        Map<Integer, PlayerActivityData.WatcherInfo> restored =
                gson.fromJson(savedWatchers.get(), mapType);
        var reloaded =
                PlayerActivityData.of()
                        .activityId(2001)
                        .scheduleId(config.getScheduleId())
                        .detail(savedDetail.get())
                        .watcherInfoMap(restored)
                        .build();
        assertEquals(42, AsterSchedule.progress(reloaded).credit());
        assertEquals(0, AsterSchedule.progress(reloaded).pendingWatcherReward());
        for (int id = 1200101; id <= 1200106; id++) {
            assertTrue(restored.get(id).isTakenReward());
            assertNotEquals(
                    0,
                    AsterWatcherRewards.take(
                            reloaded,
                            config,
                            20,
                            now,
                            id,
                            reward -> fail(),
                            reward -> fail("duplicate grant"),
                            () -> fail()));
        }
    }

    @Test
    void failedReservationSaveDoesNotChangeActualCurrencies() throws Exception {
        data.getWatcherInfoMap().get(1200101).setCurProgress(7);
        store(
                (proxy, method, args) -> {
                    throw new IllegalStateException("DB unavailable");
                });
        String before = data.getDetail();
        synchronized (player.getInventory()) {
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            AsterWatcherRewards.take(
                                    data,
                                    config,
                                    20,
                                    System.currentTimeMillis(),
                                    1200101,
                                    reward -> SalesmanRewardDelivery.validate(player, reward),
                                    reward ->
                                            SalesmanRewardDelivery.grant(player, reward, ActionReason.ActivityWatcher),
                                    data::saveSync));
        }
        assertEquals(0, player.getPrimogems());
        assertEquals(0, player.getMora());
        assertEquals(before, data.getDetail());
        assertFalse(data.getWatcherInfoMap().get(1200101).isTakenReward());
    }
}
