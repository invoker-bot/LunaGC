package emu.grasscutter.game.shop;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.beyond.BeyondCloset;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.game.player.Player;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.*;

/** Permanent historical cosmetics; no original event, date or payment prerequisite. */
public final class SpecialCosmeticShop {
    public static final int GOODS_ID_BASE = 142_000_000;

    public static String kind(int itemId) {
        var item = GameData.getItemDataMap().get(itemId);
        if (item != null)
            return switch (item.getMaterialType()) {
                case MATERIAL_COSTUME -> "costume";
                case MATERIAL_FLYCLOAK -> "flycloak";
                case MATERIAL_AVATAR_TRACE -> "trace";
                case MATERIAL_WEAPON_SKIN -> "weaponSkin";
                case MATERIAL_NAMECARD -> "namecard";
                default -> "";
            };
        return BeyondCloset.resolveCostumes(itemId).isEmpty() ? "" : "beyondCostume";
    }

    public static boolean valid(ItemData item) {
        if (item == null) return false;
        return switch (item.getMaterialType()) {
            case MATERIAL_COSTUME -> GameData.getAvatarCostumeDataItemIdMap().containsKey(item.getId());
            case MATERIAL_FLYCLOAK -> GameData.getAvatarFlycloakDataMap().containsKey(item.getId())
                    && item.getItemUseActions() != null
                    && item.getItemUseActions().stream()
                            .anyMatch(
                                    a ->
                                            a instanceof emu.grasscutter.game.props.ItemUseAction.ItemUseGainFlycloak gain
                                                    && gain.getI() == item.getId());
            case MATERIAL_AVATAR_TRACE -> GameData.getAvatarTraceEffectDataMap().containsKey(item.getId())
                    && item.getItemUseActions() != null
                    && item.getItemUseActions().stream()
                            .anyMatch(
                                    a ->
                                            a
                                                            instanceof
                                                            emu.grasscutter.game.props.ItemUseAction.ItemUseGainTraceEffect
                                                            gain
                                                    && gain.getI() == item.getId());
            case MATERIAL_WEAPON_SKIN -> GameData.getAvatarWeaponSkinDataMap().values().stream()
                    .anyMatch(s -> s.getItemId() == item.getId());
            case MATERIAL_NAMECARD -> item.getItemUseActions() != null
                    && item.getItemUseActions().stream()
                            .anyMatch(
                                    a -> a instanceof emu.grasscutter.game.props.ItemUseAction.ItemUseGainNameCard);
            default -> false;
        };
    }

    public static boolean owned(Player player, int itemId) {
        var item = GameData.getItemDataMap().get(itemId);
        if (item == null) {
            var ids = BeyondCloset.resolveCostumes(itemId);
            return !ids.isEmpty() && !player.getBeyondCloset().canGrant(ids);
        }
        return switch (item.getMaterialType()) {
            case MATERIAL_COSTUME -> CostumeShop.isOwned(player, itemId);
            case MATERIAL_FLYCLOAK -> player.getFlyCloakList().contains(itemId);
            case MATERIAL_AVATAR_TRACE -> player.getTraceEffectList().contains(itemId);
            case MATERIAL_NAMECARD -> player.getNameCardList().contains(itemId);
            case MATERIAL_WEAPON_SKIN -> GameData.getAvatarWeaponSkinDataMap().values().stream()
                    .anyMatch(s -> s.getItemId() == itemId && player.getWeaponSkinList().contains(s.getId()));
            default -> false;
        };
    }

    public static boolean canPurchase(Player player, ItemData item, int count) {
        if (item != null && item.getMaterialType() == MaterialType.MATERIAL_COSTUME)
            return CostumeShop.canPurchase(player, item, count);
        return count == 1 && valid(item) && !owned(player, item.getId());
    }

    /** GM's free unlock uses the same item actions and ownership as the native store. */
    public static boolean grant(Player player, int itemId) {
        var item = GameData.getItemDataMap().get(itemId);
        if (item == null) {
            var added = player.getBeyondCloset().grant(BeyondCloset.resolveCostumes(itemId));
            if (added.isEmpty()) return false;
            player.sendPacket(
                    new emu.grasscutter.server.packet.send.PacketBeyondAddCosmeticNotify(added));
        } else {
            if (!canPurchase(player, item, 1)) return false;
            var params =
                    new emu.grasscutter.game.props.ItemUseAction.UseItemParams(player, item.getUseTarget());
            params.usedItemId = itemId;
            if (!new emu.grasscutter.game.systems.InventorySystem(player.getServer())
                    .useItemDirect(item, params)) return false;
        }
        player.save();
        return true;
    }

    public static String purchaseFree(Player player, ShopInfo info) {
        synchronized (player) {
            int now = emu.grasscutter.utils.Utils.getCurrentSeconds();
            if (info == null
                    || info.getGoodsItem() == null
                    || info.getGoodsItem().getCount() != 1
                    || now < info.getBeginTime()
                    || now >= info.getEndTime()) throw new IllegalArgumentException("商品未上架或设置无效。");
            if (info.getScoin() != 0
                    || info.getHcoin() != 0
                    || info.getMcoin() != 0
                    || info.getBeyondMcoin() != 0
                    || info.getCostItemList() != null && !info.getCostItemList().isEmpty())
                throw new IllegalArgumentException("此商品已设置兑换价格，请在游戏内购买。");
            int itemId = info.getGoodsItem().getId();
            if (kind(itemId).isEmpty()) throw new IllegalArgumentException("商品不是外观解锁物品。");
            if (owned(player, itemId)) return "已拥有该外观，无需重复购买。";
            if (!grant(player, itemId)) throw new IllegalArgumentException("资源缺少有效的外观解锁定义。");
            player.addShopLimit(info.getGoodsId(), 1, 0);
            player.save();
            return "外观已永久解锁，请打开对应的装扮或名片页面。";
        }
    }

    public void install(Int2ObjectMap<List<ShopInfo>> shops) {
        var byShop = new TreeMap<Integer, Set<Integer>>();
        GameData.getItemDataMap().values().stream()
                .filter(SpecialCosmeticShop::valid)
                .filter(item -> item.getMaterialType() != MaterialType.MATERIAL_COSTUME)
                .forEach(item -> byShop.computeIfAbsent(902, k -> new TreeSet<>()).add(item.getId()));
        // Preserve native Beyond categories when known, including expired historical rows.
        var nativeShops = new HashMap<Integer, Integer>();
        GameData.getShopGoodsDataEntries()
                .forEach(
                        (type, rows) -> {
                            if (type >= 101000 && type <= 105000)
                                rows.forEach(row -> nativeShops.merge(row.getItemId(), type, Math::min));
                        });
        GameData.getBydMaterialDataMap().values().stream()
                .filter(item -> !BeyondCloset.resolveCostumes(item.getId()).isEmpty())
                .forEach(
                        item ->
                                byShop
                                        .computeIfAbsent(
                                                nativeShops.getOrDefault(item.getId(), 101000), k -> new TreeSet<>())
                                        .add(item.getId()));
        int total = 0;
        for (var entry : byShop.entrySet()) {
            var previous = shops.getOrDefault(entry.getKey(), List.of());
            var ids = new HashMap<Integer, Integer>();
            for (var good : previous)
                if (good.getGoodsItem() != null && entry.getValue().contains(good.getGoodsItem().getId()))
                    ids.merge(good.getGoodsItem().getId(), good.getGoodsId(), Math::min);
            var result = new ArrayList<ShopInfo>();
            for (var good : previous)
                if (good.getGoodsItem() == null || !entry.getValue().contains(good.getGoodsItem().getId()))
                    result.add(good);
            for (int item : entry.getValue()) {
                var good = new ShopInfo();
                good.setGoodsId(ids.getOrDefault(item, Math.addExact(GOODS_ID_BASE, item)));
                good.setGoodsItem(new ItemParamData(item, 1));
                good.setBuyLimit(1);
                good.setMinLevel(1);
                good.setMaxLevel(99);
                good.setEndTime(Integer.MAX_VALUE);
                good.setCostItemList(new ArrayList<>());
                result.add(good);
                total++;
            }
            shops.put(entry.getKey(), result);
        }
        Grasscutter.getLogger()
                .info(
                        "Listed {} historical gliders, echoes, weapon appearances, namecards and Beyond cosmetic products.",
                        total);
    }
}
