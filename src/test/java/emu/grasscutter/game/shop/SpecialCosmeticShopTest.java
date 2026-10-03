package emu.grasscutter.game.shop;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ItemUseAction.UseItemParams;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SpecialCosmeticShopTest {
    @TempDir Path dir;
    private static List<ItemData> materials;
    private static Set<Integer> expected;
    private Map<Integer, ItemData> previous;
    private final List<Map> maps =
            List.of(
                    GameData.getAvatarFlycloakDataMap(),
                    GameData.getAvatarTraceEffectDataMap(),
                    GameData.getAvatarWeaponSkinDataMap(),
                    GameData.getAvatarCostumeDataMap(),
                    GameData.getAvatarCostumeDataItemIdMap(),
                    GameData.getBydMaterialDataMap(),
                    GameData.getBeyondCostumeDataMap());
    private List<Map> saved;
    private static List<AvatarFlycloakData> gliders;
    private static List<AvatarTraceEffectData> traces;
    private static List<AvatarWeaponSkinData> weapons;
    private static List<AvatarCostumeData> outfits;
    private static List<BydMaterialData> beyondItems;
    private static List<BeyondCostumeData> beyondCostumes;

    @BeforeAll
    static void readResources() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var raw =
                JsonParser.parseString(
                                Files.readString(Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json")))
                        .getAsJsonArray();
        expected = new HashSet<>();
        var kinds = Set.of("MATERIAL_FLYCLOAK", "MATERIAL_AVATAR_TRACE", "MATERIAL_NAMECARD");
        for (var row : raw) {
            var obj = row.getAsJsonObject();
            if (obj.has("materialType") && kinds.contains(obj.get("materialType").getAsString()))
                expected.add(obj.get("id").getAsInt());
        }
        materials =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"), ItemData.class);
        materials.forEach(ItemData::onLoad);
        gliders =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/AvatarFlycloakExcelConfigData.json"),
                        AvatarFlycloakData.class);
        traces =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/AvatarTraceEffectExcelConfigData.json"),
                        AvatarTraceEffectData.class);
        weapons =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/AvatarWeaponSkinExcelConfigData.json"),
                        AvatarWeaponSkinData.class);
        outfits =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/AvatarCostumeExcelConfigData.json"),
                        AvatarCostumeData.class);
        beyondItems =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/BydMaterialExcelConfigData.json"),
                        BydMaterialData.class);
        beyondCostumes =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/BeyondCostumeExcelConfigData.json"),
                        BeyondCostumeData.class);
    }

    @BeforeEach
    void installResources() {
        previous = new HashMap<>(GameData.getItemDataMap());
        materials.forEach(m -> GameData.getItemDataMap().put(m.getId(), m));
        saved = maps.stream().map(m -> (Map) new HashMap(m)).toList();
        maps.forEach(Map::clear);
        gliders.forEach(r -> GameData.getAvatarFlycloakDataMap().put(r.getId(), r));
        traces.forEach(r -> GameData.getAvatarTraceEffectDataMap().put(r.getId(), r));
        weapons.forEach(r -> GameData.getAvatarWeaponSkinDataMap().put(r.getId(), r));
        outfits.forEach(
                r -> {
                    GameData.getAvatarCostumeDataMap().put(r.getId(), r);
                    if (r.getItemId() > 0) r.onLoad();
                });
        beyondItems.forEach(r -> GameData.getBydMaterialDataMap().put(r.getId(), r));
        beyondCostumes.forEach(r -> GameData.getBeyondCostumeDataMap().put(r.getId(), r));
    }

    @AfterEach
    void restore() {
        GameData.getItemDataMap().clear();
        GameData.getItemDataMap().putAll(previous);
        for (int i = 0; i < maps.size(); i++) {
            maps.get(i).clear();
            maps.get(i).putAll(saved.get(i));
        }
    }

    @Test
    void historicalCosmeticsAreFreeAndAvailableWithoutTheirOriginalActivity() {
        var manager = new ShopSystem(null);
        var rows = manager.getShopData().getOrDefault(902, List.of());
        var listed = new HashSet<Integer>();
        rows.forEach(
                g -> {
                    if (g.getGoodsItem() != null && expected.contains(g.getGoodsItem().getId())) {
                        listed.add(g.getGoodsItem().getId());
                        assertEquals(0, g.getScoin() + g.getHcoin() + g.getMcoin() + g.getBeyondMcoin());
                        assertTrue(g.getCostItemList().isEmpty());
                        assertEquals(1, g.getBuyLimit());
                        assertEquals(1, g.getGoodsItem().getCount());
                        assertEquals(0, ShopSystem.getShopNextRefreshTime(g));
                    }
                });
        assertEquals(expected, listed);
    }

    @Test
    void allWeaponAppearancesIncludingEmptyUseActionsActuallyUnlock() throws Exception {
        var player = new TestPlayer();
        var inventory = new InventorySystem(null);
        assertEquals(26, weapons.size());
        for (var skin : weapons) {
            var item = GameData.getItemDataMap().get(skin.getItemId());
            assertTrue(SpecialCosmeticShop.canPurchase(player, item, 1));
            assertFalse(SpecialCosmeticShop.canPurchase(player, item, 2));
            var params = new UseItemParams(player, item.getUseTarget());
            params.usedItemId = item.getId();
            assertTrue(inventory.useItemDirect(item, params));
            assertTrue(player.getWeaponSkinList().contains(skin.getId()));
            player.getShopLimit().clear();
            assertFalse(SpecialCosmeticShop.canPurchase(player, item, 1));
        }
        var notify =
                AvatarWeaponSkinDataNotifyOuterClass.AvatarWeaponSkinDataNotify.parseFrom(
                        new PacketAvatarWeaponSkinDataNotify(player).getData());
        assertEquals(26, notify.getWeaponSkinListCount());
        assertEquals(26, notify.getWeaponSkinIdListCount());
        assertTrue(notify.getWeaponSkinListList().stream().allMatch(s -> s.getExpireTime() == 0));
        // The two material-only placeholders have no appearance resource and must not be sold.
        assertEquals(
                2,
                materials.stream()
                        .filter(
                                i ->
                                        i.getMaterialType() == MaterialType.MATERIAL_WEAPON_SKIN
                                                && !SpecialCosmeticShop.valid(i))
                        .count());
    }

    @Test
    void everyHistoricalGliderEchoAndNamecardDeliversOwnershipAndBecomesSoldOut() {
        var player = new TestPlayer();
        var inventory = new InventorySystem(null);
        for (int id : expected) {
            var item = GameData.getItemDataMap().get(id);
            var params = new UseItemParams(player, item.getUseTarget());
            params.usedItemId = id;
            assertTrue(inventory.useItemDirect(item, params), "unlock " + id);
            assertTrue(SpecialCosmeticShop.owned(player, id), "ownership " + id);
            assertFalse(SpecialCosmeticShop.canPurchase(player, item, 1));
        }
        var shop = PacketGetShopRsp.buildShop(player, new ShopSystem(null), 902);
        for (var good : shop.getGoodsListList())
            if (expected.contains(good.getGoodsItem().getItemId())) {
                assertEquals(1, good.getSingleLimit());
                assertTrue(good.getBoughtNum() >= 1);
            }
    }

    @Test
    void historicalBeyondSuitProductsResolveGrantAndCannotRepeat() {
        var player = new TestPlayer();
        var manager = new ShopSystem(null);
        var expectedItems =
                beyondItems.stream()
                        .filter(
                                i -> !emu.grasscutter.game.beyond.BeyondCloset.resolveCostumes(i.getId()).isEmpty())
                        .map(BydMaterialData::getId)
                        .collect(java.util.stream.Collectors.toSet());
        var listed = new HashSet<Integer>();
        manager
                .getShopData()
                .forEach(
                        (type, goods) ->
                                goods.forEach(
                                        g -> {
                                            if (expectedItems.contains(g.getGoodsItem().getId())) {
                                                listed.add(g.getGoodsItem().getId());
                                                assertEquals(0, g.getMcoin() + g.getBeyondMcoin());
                                                assertEquals(1, g.getBuyLimit());
                                            }
                                        }));
        assertEquals(expectedItems, listed);
        assertEquals(976, listed.size());
        for (int id : listed) {
            if (!SpecialCosmeticShop.owned(player, id)) assertTrue(SpecialCosmeticShop.grant(player, id));
            assertTrue(SpecialCosmeticShop.owned(player, id));
            assertFalse(SpecialCosmeticShop.grant(player, id));
        }
        assertTrue(player.getBeyondCloset().toProto().size() > 1000);
    }

    @Test
    void regenerationKeepsGoodsIdsAndSavedGmOverrides() throws Exception {
        var manager = new ShopSystem(null);
        var first =
                manager.getShopData().get(902).stream()
                        .filter(g -> g.getGoodsItem().getId() == 215006)
                        .findFirst()
                        .orElseThrow();
        var catalog = new ShopCatalog(dir.resolve("overrides.json"));
        catalog.replaceBase(manager.getShopData());
        var settings = ShopCatalog.copy(first);
        settings.setMcoin(7);
        catalog.edit(902, first.getGoodsId(), "save", settings);
        catalog.edit(902, first.getGoodsId(), "disable", null);
        manager.load();
        manager.loadArtifactShop();
        catalog = new ShopCatalog(dir.resolve("overrides.json"));
        catalog.replaceBase(manager.getShopData());
        var row =
                catalog.entries().stream()
                        .filter(e -> e.goods().getGoodsId() == first.getGoodsId())
                        .findFirst()
                        .orElseThrow();
        assertFalse(row.enabled());
        assertEquals(7, row.goods().getMcoin());
        var ids = new HashSet<Integer>();
        manager.getCatalog().entries().forEach(e -> assertTrue(ids.add(e.goods().getGoodsId())));
    }

    @Test
    void freeGmUnlockIsIdempotentAndRejectsChangedPricesOrExpiredGoods() {
        var player = new TestPlayer();
        var manager = new ShopSystem(null);
        var good =
                manager.getShopData().get(902).stream()
                        .filter(g -> g.getGoodsItem().getId() == 341101)
                        .findFirst()
                        .orElseThrow();
        assertTrue(SpecialCosmeticShop.purchaseFree(player, good).contains("永久解锁"));
        assertTrue(SpecialCosmeticShop.purchaseFree(player, good).contains("已拥有"));
        var paid = ShopCatalog.copy(good);
        paid.setMcoin(1);
        assertThrows(
                IllegalArgumentException.class, () -> SpecialCosmeticShop.purchaseFree(player, paid));
        paid.setMcoin(0);
        paid.setEndTime(1);
        assertThrows(
                IllegalArgumentException.class, () -> SpecialCosmeticShop.purchaseFree(player, paid));
    }

    @Test
    void weaponAppearanceChangeIsAtomicAndChecksWeaponTypeAndOwnership() {
        var player = new TestPlayer();
        var sword = avatar(player, 10000016, "WEAPON_SWORD_ONE_HAND");
        var bow = avatar(player, 10000021, "WEAPON_BOW");
        var storage = player.getAvatars();
        assertFalse(storage.changeWeaponSkin(List.of(sword.getGuid()), 310001));
        player.addWeaponSkin(310001);
        assertFalse(storage.changeWeaponSkin(List.of(sword.getGuid(), bow.getGuid()), 310001));
        assertEquals(0, sword.getWeaponSkin());
        assertEquals(0, bow.getWeaponSkin());
        assertTrue(storage.changeWeaponSkin(List.of(sword.getGuid()), 310001));
        assertEquals(310001, sword.getWeaponSkin());
        assertTrue(storage.changeWeaponSkin(List.of(sword.getGuid()), 0));
        assertEquals(0, sword.getWeaponSkin());
        assertFalse(storage.changeWeaponSkin(List.of(), 310001));
        assertFalse(storage.changeWeaponSkin(List.of(9999L), 310001));
    }

    @Test
    void echoAndCostumeCannotBeEquippedOnAnUnrelatedCharacter() {
        var player = new TestPlayer();
        var avatar = avatar(player, 10000016, "WEAPON_SWORD_ONE_HAND");
        player.getTraceEffectList().add(215001);
        player.getCostumeList().add(200301);
        assertFalse(player.getAvatars().changeTraceEffect(avatar.getGuid(), 215001));
        assertFalse(player.getAvatars().changeCostume(avatar.getGuid(), 200301));
    }

    private static Avatar avatar(TestPlayer player, int id, String type) {
        var gson = new Gson();
        var avatar = new TestAvatar();
        try {
            var field = Avatar.class.getDeclaredField("avatarId");
            field.setAccessible(true);
            field.setInt(avatar, id);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        avatar.data(
                gson.fromJson("{\"id\":" + id + ",\"weaponType\":\"" + type + "\"}", AvatarData.class));
        assertTrue(player.getAvatars().addAvatar(avatar));
        return avatar;
    }

    private static class TestAvatar extends Avatar {
        void data(AvatarData data) {
            setAvatarData(data);
        }

        @Override
        public void save() {}
    }

    private static class TestPlayer extends Player {
        final List<BasePacket> packets = new ArrayList<>();

        @Override
        public void sendPacket(BasePacket packet) {
            packets.add(packet);
        }

        @Override
        public void save() {}

        @Override
        public emu.grasscutter.server.game.GameServer getServer() {
            return null;
        }
    }
}
