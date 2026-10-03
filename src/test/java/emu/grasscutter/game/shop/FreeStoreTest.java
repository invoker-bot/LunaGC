package emu.grasscutter.game.shop;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.game.GameSession;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class FreeStoreTest {
    private ItemData previous;
    private ItemData previousCrystals;
    @TempDir Path dir;

    static class TestPlayer extends Player {
        TestPlayer() {
            setSession(
                    new GameSession(null) {
                        @Override
                        public void send(BasePacket packet) {}
                    });
        }

        final Map<Integer, Integer> granted = new HashMap<>();
        LocalDate day = LocalDate.of(2026, 10, 1);
        Instant now;
        final BattlePassManager pass =
                new BattlePassManager(this) {
                    @Override
                    protected Instant missionNow() {
                        return now == null
                                ? day.atTime(12, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant()
                                : now;
                    }

                    @Override
                    public void save() {}
                };

        @Override
        protected LocalDate moonCardToday() {
            return day;
        }

        @Override
        public BattlePassManager getBattlePassManager() {
            return pass;
        }

        final Inventory inventory =
                new Inventory(this) {
                    @Override
                    public boolean addItem(GameItem item) {
                        return grant(item);
                    }

                    @Override
                    public boolean addItem(GameItem item, ActionReason reason) {
                        return grant(item);
                    }

                    @Override
                    public void addItems(Collection<GameItem> items, ActionReason reason) {
                        items.forEach(this::grant);
                    }

                    private boolean grant(GameItem item) {
                        granted.merge(item.getItemId(), item.getCount(), Integer::sum);
                        return true;
                    }
                };

        @Override
        public Inventory getInventory() {
            return inventory == null ? super.getInventory() : inventory;
        }

        @Override
        public void sendPacket(BasePacket packet) {}

        @Override
        public void save() {}
    }

    @BeforeEach
    void resources() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        previous =
                GameData.getItemDataMap()
                        .put(
                                201,
                                new Gson().fromJson("{\"id\":201,\"itemType\":\"ITEM_VIRTUAL\"}", ItemData.class));
        previousCrystals =
                GameData.getItemDataMap()
                        .put(
                                203,
                                new Gson().fromJson("{\"id\":203,\"itemType\":\"ITEM_VIRTUAL\"}", ItemData.class));
    }

    @AfterEach
    void restore() {
        if (previous == null) GameData.getItemDataMap().remove(201);
        else GameData.getItemDataMap().put(201, previous);
        if (previousCrystals == null) GameData.getItemDataMap().remove(203);
        else GameData.getItemDataMap().put(203, previousCrystals);
    }

    private TestPlayer activeCard(int daysAgo, int duration) {
        var player = new TestPlayer();
        player.setMoonCard(true);
        player.setMoonCardStartTime(
                Date.from(
                        player.day.minusDays(daysAgo).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()));
        player.setMoonCardDuration(duration);
        return player;
    }

    @Test
    void dailyCardRewardDoesNotExtendItsExpiryAndCannotBeTakenTwice() {
        var player = activeCard(1, 30);
        player.getTodayMoonCard();
        player.getTodayMoonCard();
        assertEquals(30, player.getMoonCardDuration());
        assertEquals(90, player.granted.get(201));
    }

    @Test
    void expiredCardDoesNotKeepGivingPrimogems() {
        var player = activeCard(120, 30);
        player.getTodayMoonCard();
        assertFalse(player.inMoonCard());
        assertTrue(player.granted.isEmpty());
    }

    @Test
    void battlePassStartsWithTheFreeTrackUntilItIsUnlocked() {
        assertFalse(new BattlePassManager(new TestPlayer()).isPaid());
    }

    @Test
    void renewalUsesRemainingDaysAndNeverRepeatsTodaysReward() {
        var player = activeCard(20, 30);
        assertTrue(player.rechargeMoonCard());
        assertEquals(40, player.getMoonCardRemainDays());
        assertTrue(player.rechargeMoonCard());
        assertEquals(70, player.getMoonCardRemainDays());
        assertEquals(90, player.granted.get(201));
        assertEquals(600, player.granted.get(203));
    }

    @Test
    void expiredCardCanBeBoughtAgainAndEndsOnTheThirtiethDay() {
        var player = activeCard(300, 180);
        assertTrue(player.rechargeMoonCard());
        assertEquals(30, player.getMoonCardRemainDays());
        player.day = player.day.plusDays(29);
        player.getTodayMoonCard();
        assertEquals(1, player.getMoonCardRemainDays());
        player.day = player.day.plusDays(1);
        player.getTodayMoonCard();
        assertFalse(player.inMoonCard());
        assertEquals(180, player.granted.get(201));
    }

    @Test
    void remainingDaysCapRejectsTheWholeRenewal() {
        var player = activeCard(0, 180);
        assertFalse(player.rechargeMoonCard());
        assertTrue(player.granted.isEmpty());
    }

    FreeStore store(ShopCatalog catalog) {
        var store =
                new FreeStore(catalog) {
                    @Override
                    protected void persist(Player player) {}
                };
        store.load();
        assertFalse(
                store.products().isEmpty(), "The resource product tables must be mounted for this test");
        return store;
    }

    @Test
    void desktopShopUsesDesktopProductsInsteadOfCloudGameProducts() {
        var store = store(new ShopCatalog(dir.resolve("shop.json")));
        for (var product : store.products()) {
            if (product.kind().equals("primogems")) continue;
            assertFalse(product.productId().startsWith("cloud"), product.productId());
        }
        var shop = emu.grasscutter.net.proto.ShopOuterClass.Shop.newBuilder().setShopType(903);
        store.addProducts(shop, new TestPlayer());
        assertEquals(6, shop.getMcoinProductListCount());
        assertEquals("ys_chn_primogem1ststall_tier1", shop.getMcoinProductList(0).getProductId());
        assertEquals("Tier_1", shop.getMcoinProductList(0).getPriceTier());
        var card = emu.grasscutter.net.proto.ShopOuterClass.Shop.newBuilder().setShopType(902);
        store.addProducts(card, new TestPlayer());
        assertEquals("ys_chn_blessofmoon_tier5", card.getCardProductList(0).getProductId());
        assertEquals("Tier_5", card.getCardProductList(0).getPriceTier());
    }

    @Test
    void sdkPriceTableKeepsResourceTierNamesWithZeroPrices() {
        var store = store(new ShopCatalog(dir.resolve("shop.json")));
        var json = new Gson().toJsonTree(store.priceTiers(false)).getAsJsonObject();
        var tiers = new HashSet<String>();
        for (var row : json.getAsJsonArray("tiers")) {
            var tier = row.getAsJsonObject();
            tiers.add(tier.get("tier_id").getAsString());
            var price = tier.getAsJsonArray("t_price").get(0).getAsJsonObject();
            assertEquals("0", price.get("price").getAsString());
            assertEquals("CNY", price.get("currency").getAsString());
        }
        assertTrue(tiers.containsAll(Set.of("Tier_1", "Tier_5", "Tier_10", "Tier_20")));
        assertFalse(tiers.contains("Tier_0"));
    }

    @Test
    void receiptRetryAndDisabledProductDoNotDuplicateGrants() throws Exception {
        var catalog = new ShopCatalog(dir.resolve("shop.json"));
        var store = store(catalog);
        var player = new TestPlayer();
        String receipt = UUID.randomUUID().toString();
        store.purchase(player, "primogems:1", receipt);
        store.purchase(player, "primogems:1", receipt);
        assertEquals(60, player.granted.get(201));
        assertThrows(
                IllegalArgumentException.class, () -> store.purchase(player, "crystals:1", receipt));
        catalog.setProductEnabled("primogems:1", false);
        assertThrows(
                IllegalArgumentException.class,
                () -> store.purchase(player, "primogems:1", UUID.randomUUID().toString()));
        assertEquals(60, player.granted.get(201));
    }

    @Test
    void crystalBonusesComeFromResourcesAndUnknownClientProductsAreRejected() {
        var store = store(new ShopCatalog(dir.resolve("shop.json")));
        var player = new TestPlayer();
        var p =
                store.products().stream()
                        .filter(i -> i.key().equals("crystals:2"))
                        .findFirst()
                        .orElseThrow();
        store.purchaseById(player, p.productId(), "crystals");
        assertEquals(600, player.granted.get(203));
        store.purchaseById(player, p.productId(), "crystals");
        assertEquals(930, player.granted.get(203));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.purchaseById(player, "forged_product", "crystals"));
        assertThrows(
                IllegalArgumentException.class, () -> store.purchaseById(player, p.productId(), "card"));
    }

    @Test
    void beyondRechargeUsesItsOwnBalanceAndResourceProducts() throws Exception {
        var store = store(new ShopCatalog(dir.resolve("shop.json")));
        var player = new TestPlayer();
        var p =
                store.products().stream()
                        .filter(i -> i.key().equals("beyond_crystals:10002"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(270, p.amount());
        store.purchaseById(player, p.productId(), "beyond_crystals");
        assertEquals(300, player.getProperty(BeyondCurrency.propertyForItem(231)));
        assertEquals(0, player.getCrystals());
        assertEquals(0, player.getPrimogems());
        store.purchaseById(player, p.productId(), "beyond_crystals");
        assertEquals(600, player.getProperty(BeyondCurrency.propertyForItem(231)));
        var builder = emu.grasscutter.net.proto.ShopOuterClass.Shop.newBuilder().setShopType(100000);
        store.addProducts(builder, player);
        assertEquals(6, builder.getBeyondMcoinProductListCount());
        assertEquals(0, builder.getMcoinProductListCount());
        assertTrue(
                builder.getBeyondMcoinProductListList().stream()
                        .allMatch(i -> i.getProductId().contains("beyondgem")));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.purchaseById(player, p.productId(), "crystals"));
        player.setProperty(BeyondCurrency.propertyForItem(231), Integer.MAX_VALUE);
        assertThrows(
                IllegalArgumentException.class,
                () -> store.purchaseById(player, p.productId(), "beyond_crystals"));
        assertEquals(Integer.MAX_VALUE, player.getProperty(BeyondCurrency.propertyForItem(231)));
    }

    @Test
    void battlePassUnlockIsExplicitAndCannotBeRepeated() {
        var player = new TestPlayer();
        assertTrue(player.pass.unlockPaid(false));
        assertTrue(player.pass.isPaid());
        assertFalse(player.pass.unlockPaid(false));
        player.pass.setLevel(49);
        player.pass.addPointsDirectly(10000, false);
        assertEquals(50, player.pass.getLevel());
        assertEquals(0, player.pass.getPoint());
        assertEquals(0, player.pass.getCyclePoints());
    }

    @Test
    void scheduleChangeResetsPremiumAccessButFirstAssignmentKeepsLegacyProgress() {
        var player = new TestPlayer();
        player.pass.setLevel(10);
        player.pass.unlockPaid(false);
        player.pass.synchronizeSchedule();
        assertEquals(10, player.pass.getLevel());
        assertTrue(player.pass.isPaid());
        var schedules = GameData.getBattlePassScheduleDataMap();
        int initial = BattlePassScheduleData.currentId();
        int different = initial == 7100 ? 7000 : 7100;
        var previous = new HashMap<Integer, BattlePassScheduleData>(schedules);
        try {
            schedules.clear();
            schedules.put(different, new BattlePassScheduleData());
            player.pass.synchronizeSchedule();
            assertEquals(0, player.pass.getLevel());
            assertFalse(player.pass.isPaid());
        } finally {
            schedules.clear();
            schedules.putAll(previous);
        }
    }

    @Test
    void extraBattlePassRewardUsesCurrentResourcesExactlyOnce() {
        var schedules = GameData.getBattlePassScheduleDataMap();
        var rewards = GameData.getRewardDataMap();
        var oldSchedule =
                schedules.put(
                        7100,
                        new Gson()
                                .fromJson(
                                        "{\"id\":7100,\"levelRewardIndexId\":1,\"extraPaidRewardId\":1000071,\"extraPaidAddPoint\":10000}",
                                        BattlePassScheduleData.class));
        var oldReward =
                rewards.put(
                        1000071,
                        new Gson()
                                .fromJson(
                                        "{\"id\":1000071,\"rewardItemList\":[{\"itemId\":201,\"itemCount\":680}]}",
                                        RewardData.class));
        try {
            var player = new TestPlayer();
            assertTrue(player.pass.unlockPaid(true));
            assertTrue(player.pass.isExtraPaidRewardTaken());
            assertEquals(10, player.pass.getLevel());
            assertEquals(680, player.granted.get(201));
            assertFalse(player.pass.unlockPaid(true));
            assertEquals(680, player.granted.get(201));
        } finally {
            if (oldSchedule == null) schedules.remove(7100);
            else schedules.put(7100, oldSchedule);
            if (oldReward == null) rewards.remove(1000071);
            else rewards.put(1000071, oldReward);
        }
    }
}
