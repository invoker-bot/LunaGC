package emu.grasscutter.server.http.handlers;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import dev.morphia.Datastore;
import dev.morphia.InsertOneOptions;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.reliquary.*;
import emu.grasscutter.data.excels.weapon.WeaponPromoteData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.*;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class GmEquipmentTest {
    private static final List<Runnable> restore = new ArrayList<>();

    @BeforeAll
    static void resources() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        load("WeaponExcelConfigData.json", ItemData.class, GameData.getItemDataMap());
        load("ReliquaryExcelConfigData.json", ItemData.class, GameData.getItemDataMap());
        load(
                "WeaponPromoteExcelConfigData.json",
                WeaponPromoteData.class,
                GameData.getWeaponPromoteDataMap());
        load(
                "ReliquaryMainPropExcelConfigData.json",
                ReliquaryMainPropData.class,
                GameData.getReliquaryMainPropDataMap());
        load(
                "ReliquaryAffixExcelConfigData.json",
                ReliquaryAffixData.class,
                GameData.getReliquaryAffixDataMap());
        load(
                "ReliquaryLevelExcelConfigData.json",
                ReliquaryLevelData.class,
                GameData.getMapByResourceDef(ReliquaryLevelData.class));
    }

    @SuppressWarnings("unchecked")
    private static <T extends GameResource> void load(
            String file, Class<T> type, Int2ObjectMap<?> target) throws Exception {
        var map = (Int2ObjectMap<T>) target;
        var previous = new Int2ObjectOpenHashMap<>(map);
        restore.add(
                () -> {
                    map.clear();
                    map.putAll(previous);
                });
        for (var row : JsonUtils.loadToList(Path.of("resources/ExcelBinOutput/" + file), type)) {
            row.onLoad();
            map.put(row.getId(), row);
        }
    }

    @AfterAll
    static void reset() {
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
    }

    private GmEquipment.Request request(int id, int level) {
        var r = new GmEquipment.Request();
        r.itemId = id;
        r.level = level;
        return r;
    }

    @Test
    void weaponUsesRealCapAndRefinement() {
        var r = request(11101, 70);
        var items = GmEquipment.create(r);
        assertEquals(70, items.get(0).getLevel());
        assertEquals(4, items.get(0).getPromoteLevel());
        assertEquals(0, items.get(0).getRefinement());
        assertThrows(IllegalArgumentException.class, () -> GmEquipment.create(request(11101, 90)));
        r.refinement = 2;
        assertThrows(IllegalArgumentException.class, () -> GmEquipment.create(r));
        assertEquals(70, GmEquipment.options(11101).get("maxLevel"));
    }

    @Test
    void canChooseBeforeAndAfterAscensionAtBoundary() {
        var r = request(11501, 20);
        r.promoteLevel = 0;
        assertEquals(0, GmEquipment.create(r).get(0).getPromoteLevel());
        r.promoteLevel = 1;
        assertEquals(1, GmEquipment.create(r).get(0).getPromoteLevel());
        r.promoteLevel = 2;
        assertThrows(IllegalArgumentException.class, () -> GmEquipment.create(r));
        r.level = 90;
        r.promoteLevel = null;
        r.refinement = 5;
        assertEquals(4, GmEquipment.create(r).get(0).getRefinement());
    }

    @Test
    void artifactDisplayLevelAndRollsMatchResources() {
        for (int id : new int[] {75542, 75543, 75544}) {
            var data = GameData.getItemDataMap().get(id);
            for (int level : new int[] {0, 4, 20}) {
                var item = GmEquipment.create(request(id, level)).get(0);
                assertEquals(level + 1, item.getLevel());
                assertEquals(data.getAppendPropNum() + level / 4, item.getAppendPropIdList().size());
                assertEquals(
                        Math.min(4, item.getAppendPropIdList().size()),
                        item.getAppendPropIdList().stream()
                                .map(a -> GameData.getReliquaryAffixDataMap().get(a).getFightProp())
                                .distinct()
                                .count());
                assertEquals(
                        FightProperty.FIGHT_PROP_HP,
                        GameData.getReliquaryMainPropDataMap().get(item.getMainPropId()).getFightProp());
            }
        }
        assertEquals(20, GmEquipment.options(75544).get("maxLevel"));
        assertThrows(IllegalArgumentException.class, () -> GmEquipment.create(request(75544, 21)));
    }

    @Test
    void customizedRollsStillFillMissingDistinctStats() {
        var r = request(75543, 20);
        r.substats = List.of(new GmEquipment.Substat(501224, 5));
        var item = GmEquipment.create(r).get(0);
        assertEquals(8, item.getAppendPropIdList().size());
        assertEquals(5, Collections.frequency(item.getAppendPropIdList(), 501224));
        assertEquals(
                4,
                item.getAppendPropIdList().stream()
                        .map(a -> GameData.getReliquaryAffixDataMap().get(a).getFightProp())
                        .distinct()
                        .count());
    }

    @Test
    void rejectsWrongDepotDuplicateMainAndExcessRolls() {
        var r = request(75544, 20);
        r.mainPropId = 10001;
        assertThrows(IllegalArgumentException.class, () -> GmEquipment.create(r));
        r.mainPropId = 0;
        r.substats = List.of(new GmEquipment.Substat(401224, 1));
        assertThrows(IllegalArgumentException.class, () -> GmEquipment.create(r));
        r.substats = List.of(new GmEquipment.Substat(501024, 1)); // HP conflicts with flower's HP main.
        assertThrows(IllegalArgumentException.class, () -> GmEquipment.create(r));
        r.substats = List.of(new GmEquipment.Substat(501224, 1), new GmEquipment.Substat(501223, 1));
        assertThrows(IllegalArgumentException.class, () -> GmEquipment.create(r));
        r.substats = List.of(new GmEquipment.Substat(501224, 7)); // 7 + three missing stats > 9.
        assertThrows(IllegalArgumentException.class, () -> GmEquipment.create(r));
        r.substats = List.of(new GmEquipment.Substat(501224, -1));
        assertThrows(IllegalArgumentException.class, () -> GmEquipment.create(r));
    }

    @Test
    void randomMainCannotConflictWithCustomSubstat() {
        var r = request(75514, 20); // goblet
        r.substats = List.of(new GmEquipment.Substat(501234, 1));
        for (int i = 0; i < 30; i++) {
            var item = GmEquipment.create(r).get(0);
            assertNotEquals(
                    FightProperty.FIGHT_PROP_CHARGE_EFFICIENCY,
                    GameData.getReliquaryMainPropDataMap().get(item.getMainPropId()).getFightProp());
        }
    }

    @Test
    void invalidQuantityAndWeaponArtifactParametersAreRejected() {
        var r = request(11501, 90);
        r.amount = 101;
        assertThrows(IllegalArgumentException.class, () -> GmEquipment.create(r));
        r.amount = 0;
        assertThrows(IllegalArgumentException.class, () -> GmEquipment.create(r));
        r.amount = 1;
        r.mainPropId = 40001;
        assertThrows(IllegalArgumentException.class, () -> GmEquipment.create(r));
    }

    @Test
    void httpJsonPayloadKeepsDefaultsAndCustomRolls() {
        var request =
                JsonUtils.decode(
                        "{\"itemId\":75543,\"target\":12345,\"level\":20,\"mainPropId\":14001,\"substats\":[{\"affixId\":501224,\"rolls\":5}]}",
                        GmEquipment.Request.class);
        assertEquals(1, request.amount);
        assertEquals(1, request.refinement);
        var item = GmEquipment.create(request).get(0);
        assertEquals(21, item.getLevel());
        assertEquals(14001, item.getMainPropId());
        assertEquals(5, Collections.frequency(item.getAppendPropIdList(), 501224));
    }

    private static final class TestPlayer extends Player {
        @Override
        public int getUid() {
            return 12345;
        }

        @Override
        public void sendPacket(BasePacket packet) {}
    }

    private static void drain() throws Exception {
        var executor = (ThreadPoolExecutor) DatabaseHelper.getEventExecutor();
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (executor.getActiveCount() != 0 || !executor.getQueue().isEmpty()) {
            if (System.nanoTime() > end) fail("Pending test database jobs");
            Thread.sleep(5);
        }
    }

    @Test
    void chosenEquipmentIsActuallyStoredAndAcknowledged() throws Exception {
        drain();
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        var original = field.get(null);
        var saved = Collections.synchronizedList(new ArrayList<GameItem>());
        var gson =
                new GsonBuilder()
                        .setExclusionStrategies(
                                new ExclusionStrategy() {
                                    @Override
                                    public boolean shouldSkipField(FieldAttributes field) {
                                        return field.getAnnotation(dev.morphia.annotations.Transient.class) != null;
                                    }

                                    @Override
                                    public boolean shouldSkipClass(Class<?> type) {
                                        return false;
                                    }
                                })
                        .create();
        try {
            field.set(
                    null,
                    Proxy.newProxyInstance(
                            Datastore.class.getClassLoader(),
                            new Class<?>[] {Datastore.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("save")) {
                                    if (args.length > 1 && args[0] instanceof GameItem item) {
                                        var options = (InsertOneOptions) args[1];
                                        assertEquals("majority", options.writeConcern().getWString());
                                        assertEquals(Boolean.TRUE, options.writeConcern().getJournal());
                                        saved.add(gson.fromJson(gson.toJson(item), GameItem.class));
                                    }
                                    return args[0];
                                }
                                return null;
                            }));
            var player = new TestPlayer();
            var weapon = request(11501, 80);
            weapon.promoteLevel = 6;
            weapon.refinement = 5;
            weapon.amount = 2;
            assertEquals(2, GmEquipment.grant(player, GmEquipment.create(weapon)));
            var relic = request(75543, 20);
            relic.substats = List.of(new GmEquipment.Substat(501224, 5));
            assertEquals(1, GmEquipment.grant(player, GmEquipment.create(relic)));
            drain();
            assertEquals(3, player.getInventory().getItems().size());
            assertEquals(3, saved.size());
            assertTrue(saved.stream().allMatch(i -> i.getOwnerId() == 12345));
            var w = saved.stream().filter(i -> i.getItemId() == 11501).findFirst().orElseThrow();
            assertEquals(80, w.getLevel());
            assertEquals(6, w.getPromoteLevel());
            assertEquals(4, w.getRefinement());
            var a = saved.stream().filter(i -> i.getItemId() == 75543).findFirst().orElseThrow();
            assertEquals(21, a.getLevel());
            assertEquals(8, a.getAppendPropIdList().size());
            assertEquals(5, Collections.frequency(a.getAppendPropIdList(), 501224));
        } finally {
            drain();
            field.set(null, original);
        }
    }

    @Test
    void fullInventoryIsRejectedBeforeAnySave() throws Exception {
        var player = new TestPlayer();
        player.getInventory().createInventoryTab(ItemType.ITEM_WEAPON, new EquipInventoryTab(1));
        var r = request(11501, 90);
        r.amount = 2;
        assertThrows(
                IllegalArgumentException.class, () -> GmEquipment.grant(player, GmEquipment.create(r)));
        assertEquals(0, player.getInventory().getItems().size());
    }

    @Test
    void saveFailureDoesNotReportSuccessOrRetryAward() throws Exception {
        drain();
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        var original = field.get(null);
        var writes = new AtomicInteger();
        try {
            field.set(
                    null,
                    Proxy.newProxyInstance(
                            Datastore.class.getClassLoader(),
                            new Class<?>[] {Datastore.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("save")) {
                                    if (args.length > 1 && args[0] instanceof GameItem) {
                                        writes.incrementAndGet();
                                        throw new IllegalStateException("simulated failure");
                                    }
                                    return args[0];
                                }
                                return null;
                            }));
            var player = new TestPlayer();
            var r = request(11501, 90);
            r.amount = 2;
            var error =
                    assertThrows(
                            IllegalStateException.class, () -> GmEquipment.grant(player, GmEquipment.create(r)));
            assertTrue(error.getMessage().contains("勿重复发放"));
            assertEquals(1, player.getInventory().getItems().size());
            assertEquals(1, writes.get());
        } finally {
            drain();
            field.set(null, original);
        }
    }
}
