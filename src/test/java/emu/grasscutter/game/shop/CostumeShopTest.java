package emu.grasscutter.game.shop;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.AvatarCostumeData;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ItemUseAction.UseItemParams;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.AvatarGainCostumeNotifyOuterClass.AvatarGainCostumeNotify;
import emu.grasscutter.server.packet.send.PacketGetShopRsp;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class CostumeShopTest {
    private static final int SHOP = 1052;
    private final Map<Integer, ItemData> oldItems = new HashMap<>();
    private final Map<Integer, AvatarCostumeData> oldCostumes = new HashMap<>();
    private final Map<Integer, AvatarCostumeData> oldCostumeItems = new HashMap<>();
    private List<ShopGoodsData> oldGoods;
    private List<AvatarCostumeData> unlockable;
    private List<ShopGoodsData> official;
    @TempDir Path dir;
    private static List<AvatarCostumeData> resourceCostumes;
    private static List<ItemData> resourceItems;
    private static List<ShopGoodsData> resourceGoods;

    @BeforeAll
    static void readResources() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        resourceCostumes =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/AvatarCostumeExcelConfigData.json"),
                        AvatarCostumeData.class);
        resourceItems =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"), ItemData.class)
                        .stream()
                        .filter(item -> item.getMaterialType() == MaterialType.MATERIAL_COSTUME)
                        .toList();
        resourceItems.forEach(ItemData::onLoad);
        resourceGoods =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/ShopGoodsExcelConfigData.json"),
                                ShopGoodsData.class)
                        .stream()
                        .filter(row -> row.getShopType() == SHOP)
                        .toList();
        resourceGoods.forEach(ShopGoodsData::onLoad);
    }

    @BeforeEach
    void resources() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        oldCostumes.putAll(GameData.getAvatarCostumeDataMap());
        oldCostumeItems.putAll(GameData.getAvatarCostumeDataItemIdMap());
        GameData.getAvatarCostumeDataMap().clear();
        GameData.getAvatarCostumeDataItemIdMap().clear();
        var rows = resourceCostumes;
        for (var row : rows) {
            GameData.getAvatarCostumeDataMap().put(row.getId(), row);
            row.onLoad();
        }
        unlockable = rows.stream().filter(row -> row.getItemId() > 0 && row.getQuality() >= 4).toList();
        for (var item : resourceItems) {
            oldItems.put(item.getId(), GameData.getItemDataMap().put(item.getId(), item));
        }
        official = resourceGoods;
        oldGoods = GameData.getShopGoodsDataEntries().put(SHOP, official);
    }

    @AfterEach
    void restore() {
        oldItems.forEach(
                (id, item) -> {
                    if (item == null) GameData.getItemDataMap().remove(id);
                    else GameData.getItemDataMap().put(id, item);
                });
        GameData.getAvatarCostumeDataMap().clear();
        GameData.getAvatarCostumeDataMap().putAll(oldCostumes);
        GameData.getAvatarCostumeDataItemIdMap().clear();
        GameData.getAvatarCostumeDataItemIdMap().putAll(oldCostumeItems);
        if (oldGoods == null) GameData.getShopGoodsDataEntries().remove(SHOP);
        else GameData.getShopGoodsDataEntries().put(SHOP, oldGoods);
    }

    @Test
    void everyResourceOutfitIsListedForFreeWithOnePermanentUnlock() {
        assertEquals(30, unlockable.size(), "Update the resource audit when the catalog grows");
        var shop = PacketGetShopRsp.buildShop(new TestPlayer(), new ShopSystem(null), SHOP);
        assertEquals(unlockable.size(), shop.getGoodsListCount());
        assertEquals(
                new HashSet<>(unlockable.stream().map(AvatarCostumeData::getItemId).toList()),
                new HashSet<>(
                        shop.getGoodsListList().stream().map(g -> g.getGoodsItem().getItemId()).toList()));
        for (var goods : shop.getGoodsListList()) {
            assertEquals(1, goods.getGoodsItem().getCount());
            assertEquals(1, goods.getSingleLimit());
            assertEquals(
                    0, goods.getScoin() + goods.getHcoin() + goods.getMcoin() + goods.getBeyondMcoin());
            assertEquals(0, goods.getCostItemListCount());
            assertEquals(0, goods.getNextRefreshTime());
        }
    }

    @Test
    void missingTravelerDurinAndAlternateOutfitsHaveStableDistinctGoodsIds() {
        var manager = new ShopSystem(null);
        var goods = manager.getShopData().get(SHOP);
        for (int item : new int[] {340006, 340007, 340008, 340009, 340023, 340024, 340025}) {
            assertEquals(1, goods.stream().filter(g -> g.getGoodsItem().getId() == item).count());
        }
        var before =
                goods.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        g -> g.getGoodsItem().getId(), ShopInfo::getGoodsId));
        manager.load();
        manager.loadArtifactShop();
        var after =
                manager.getShopData().get(SHOP).stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        g -> g.getGoodsItem().getId(), ShopInfo::getGoodsId));
        assertEquals(before, after);
        assertEquals(30, after.size());
        assertEquals(30, new HashSet<>(after.values()).size());
        for (var row : official) assertEquals(row.getGoodsId(), after.get(row.getItemId()));
    }

    @Test
    void anOutfitGrantedElsewhereIsAlreadySoldOutEvenWithAnotherGoodsIdOrNoLimit() {
        var player = new TestPlayer();
        player.getCostumeList().add(201401);
        var source = new Int2ObjectOpenHashMap<List<ShopInfo>>();
        var good = new ShopInfo();
        good.setGoodsId(99999);
        good.setGoodsItem(new ItemParamData(340000, 1));
        good.setBuyLimit(0);
        source.put(SHOP, List.of(good));
        var manager = new ShopSystem(null);
        manager.getCatalog().replaceBase(source);
        var shown = PacketGetShopRsp.buildShop(player, manager, SHOP).getGoodsList(0);
        assertEquals(1, shown.getSingleLimit());
        assertEquals(1, shown.getBoughtNum());
    }

    @Test
    void allThirtyItemsActuallyUnlockTheirResourceSkinAndNotifyThePlayer() throws Exception {
        var player = new TestPlayer();
        var inventory = new InventorySystem(null);
        for (var row : unlockable) {
            var item = GameData.getItemDataMap().get(row.getItemId());
            var params = new UseItemParams(player, item.getUseTarget());
            params.usedItemId = item.getId();
            assertTrue(inventory.useItemDirect(item, params));
            assertTrue(player.getCostumeList().contains(row.getId()));
            assertEquals(
                    row.getId(),
                    AvatarGainCostumeNotify.parseFrom(player.packets.get(player.packets.size() - 1).getData())
                            .getCostumeId());
        }
        assertEquals(30, player.getCostumeList().size());
        var shop = PacketGetShopRsp.buildShop(player, new ShopSystem(null), SHOP);
        assertEquals(30, shop.getGoodsListCount());
        assertTrue(shop.getGoodsListList().stream().allMatch(g -> g.getBoughtNum() == 1));
    }

    @Test
    void gmDisableAndPriceOverrideSurviveRegeneratingTheCompleteSkinCatalog() throws Exception {
        var manager = new ShopSystem(null);
        var catalog = new ShopCatalog(dir.resolve("ShopOverrides.json"));
        catalog.replaceBase(manager.getShopData());
        var goods = manager.getShopData().get(SHOP);
        var disabled =
                goods.stream().filter(g -> g.getGoodsItem().getId() == 340025).findFirst().orElseThrow();
        var edited =
                goods.stream().filter(g -> g.getGoodsItem().getId() == 340024).findFirst().orElseThrow();
        catalog.edit(SHOP, disabled.getGoodsId(), "disable", null);
        var priced = ShopCatalog.copy(edited);
        priced.setMcoin(10);
        catalog.edit(SHOP, edited.getGoodsId(), "save", priced);
        manager.load();
        var reloaded = new ShopCatalog(dir.resolve("ShopOverrides.json"));
        reloaded.replaceBase(manager.getShopData());
        assertEquals(29, reloaded.active().get(SHOP).size());
        assertFalse(
                reloaded.active().get(SHOP).stream()
                        .anyMatch(g -> g.getGoodsId() == disabled.getGoodsId()));
        assertEquals(
                10,
                reloaded.active().get(SHOP).stream()
                        .filter(g -> g.getGoodsId() == edited.getGoodsId())
                        .findFirst()
                        .orElseThrow()
                        .getMcoin());
    }

    @Test
    void aFutureResourceOutfitIsAddedWithoutEditingTheShopSnapshot() {
        var gson = new Gson();
        var skin =
                gson.fromJson(
                        "{\"skinId\":212399,\"itemId\":349999,\"quality\":5}", AvatarCostumeData.class);
        var item =
                gson.fromJson(
                        "{\"id\":349999,\"itemType\":\"ITEM_MATERIAL\",\"materialType\":\"MATERIAL_COSTUME\",\"useOnGain\":true,\"itemUse\":[{\"useOp\":\"ITEM_USE_GAIN_COSTUME\",\"useParam\":[\"212399\"]}]}",
                        ItemData.class);
        GameData.getAvatarCostumeDataMap().put(skin.getId(), skin);
        skin.onLoad();
        item.onLoad();
        oldItems.put(item.getId(), GameData.getItemDataMap().put(item.getId(), item));
        var manager = new ShopSystem(null);
        var added =
                manager.getShopData().get(SHOP).stream()
                        .filter(g -> g.getGoodsItem().getId() == item.getId())
                        .findFirst()
                        .orElseThrow();
        assertEquals(CostumeShop.GOODS_ID_BASE + skin.getId(), added.getGoodsId());
        assertTrue(added.getGoodsId() < ShopCatalog.CUSTOM_ID_BASE);
        assertTrue(CostumeShop.canPurchase(new TestPlayer(), item, 1));
        assertEquals(31, manager.getShopData().get(SHOP).size());
    }

    @Test
    void defaultOrUnresolvableSkinItemsCannotBeSoldOrPurchased() {
        var gson = new Gson();
        for (boolean isDefault : new boolean[] {true, false}) {
            var skin =
                    gson.fromJson(
                            "{\"skinId\":299999,\"itemId\":349999,\"quality\":5,\"isDefault\":" + isDefault + "}",
                            AvatarCostumeData.class);
            var item =
                    gson.fromJson(
                            "{\"id\":349999,\"itemType\":\"ITEM_MATERIAL\",\"materialType\":\"MATERIAL_COSTUME\",\"useOnGain\":true,\"itemUse\":[{\"useOp\":\"ITEM_USE_GAIN_COSTUME\",\"useParam\":[\""
                                    + (isDefault ? 299999 : 299998)
                                    + "\"]}]}",
                            ItemData.class);
            GameData.getAvatarCostumeDataMap().put(skin.getId(), skin);
            skin.onLoad();
            item.onLoad();
            if (!oldItems.containsKey(item.getId()))
                oldItems.put(item.getId(), GameData.getItemDataMap().get(item.getId()));
            GameData.getItemDataMap().put(item.getId(), item);
            var manager = new ShopSystem(null);
            assertEquals(30, manager.getShopData().get(SHOP).size());
            assertFalse(CostumeShop.canPurchase(new TestPlayer(), item, 1));
        }
    }

    @Test
    void ownershipRejectsRepeatPurchasesAndBatchesEvenWhenTheGoodsLimitChanges() {
        var player = new TestPlayer();
        var item = GameData.getItemDataMap().get(340023);
        assertTrue(CostumeShop.canPurchase(player, item, 1));
        assertFalse(CostumeShop.canPurchase(player, item, 2));
        assertFalse(CostumeShop.canPurchase(player, item, 0));
        assertFalse(CostumeShop.canPurchase(player, item, -1));
        var params = new UseItemParams(player, item.getUseTarget());
        params.usedItemId = item.getId();
        assertTrue(new InventorySystem(null).useItemDirect(item, params));
        assertFalse(CostumeShop.canPurchase(player, item, 1));
        player.getShopLimit().clear();
        assertFalse(CostumeShop.canPurchase(player, item, 1));
        assertEquals(Set.of(200501), player.getCostumeList());
    }

    private static class TestPlayer extends Player {
        final List<BasePacket> packets = new ArrayList<>();

        @Override
        public void sendPacket(BasePacket packet) {
            packets.add(packet);
        }

        @Override
        public void save() {}
    }
}
