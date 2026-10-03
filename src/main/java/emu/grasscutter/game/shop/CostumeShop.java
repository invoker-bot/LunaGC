package emu.grasscutter.game.shop;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.avatar.AvatarCostumeData;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ItemUseAction.ItemUseGainCostume;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.*;

/** All resource-backed four- and five-star character outfits, including missing shop rows. */
public final class CostumeShop {
    // Below GM's custom goods (150 million) and the artifact catalog (200 million).
    public static final int GOODS_ID_BASE = 140_000_000;
    private static final int SHOP = ShopType.SHOP_TYPE_COSTUME.shopTypeId;

    public void install(Int2ObjectMap<List<ShopInfo>> shopData) {
        var outfits =
                GameData.getAvatarCostumeDataMap().values().stream()
                        .filter(skin -> skin.getQuality() >= 4 && !skin.isDefault() && skin.getItemId() > 0)
                        .filter(skin -> skin == costumeForItem(GameData.getItemDataMap().get(skin.getItemId())))
                        .sorted(Comparator.comparingInt(AvatarCostumeData::getItemId))
                        .toList();
        // ShopSystem is also constructed before resources have loaded.
        if (outfits.isEmpty()) return;

        var previous = shopData.getOrDefault(SHOP, List.of());
        var ids = new HashMap<Integer, Integer>();
        var itemIds = new HashSet<Integer>();
        outfits.forEach(skin -> itemIds.add(skin.getItemId()));
        for (var good : previous) {
            if (good.getGoodsItem() != null && good.getGoodsId() > 0) {
                ids.merge(good.getGoodsItem().getId(), good.getGoodsId(), Math::min);
            }
        }
        var result = new ArrayList<ShopInfo>();
        for (var good : previous) {
            boolean generated =
                    good.getGoodsId() >= GOODS_ID_BASE && good.getGoodsId() < ShopCatalog.CUSTOM_ID_BASE;
            if (!generated
                    && (good.getGoodsItem() == null || !itemIds.contains(good.getGoodsItem().getId()))) {
                result.add(good);
            }
        }
        for (var skin : outfits) {
            int id = ids.getOrDefault(skin.getItemId(), Math.addExact(GOODS_ID_BASE, skin.getId()));
            var good = new ShopInfo();
            good.setGoodsId(id);
            good.setGoodsItem(new ItemParamData(skin.getItemId(), 1));
            good.setBuyLimit(1);
            good.setMinLevel(1);
            good.setMaxLevel(99);
            good.setEndTime(Integer.MAX_VALUE);
            good.setCostItemList(new ArrayList<>());
            // Default prices are zero; saved GM overrides are applied by ShopCatalog afterwards.
            result.add(good);
        }
        shopData.put(SHOP, result);
        Grasscutter.getLogger()
                .info("Listed {} rare character outfits in shop {}.", outfits.size(), SHOP);
    }

    private static AvatarCostumeData costumeForItem(ItemData item) {
        if (item == null
                || item.getMaterialType() != MaterialType.MATERIAL_COSTUME
                || !item.isUseOnGain()) return null;
        var skin = GameData.getAvatarCostumeDataItemIdMap().get(item.getId());
        if (skin == null || skin.isDefault() || item.getItemUseActions() == null) return null;
        boolean unlocks =
                item.getItemUseActions().stream()
                        .anyMatch(
                                action -> action instanceof ItemUseGainCostume gain && gain.getI() == skin.getId());
        return unlocks ? skin : null;
    }

    public static boolean isOwned(Player player, int itemId) {
        var skin = GameData.getAvatarCostumeDataItemIdMap().get(itemId);
        return skin != null && player.getCostumeList().contains(skin.getId());
    }

    /** Ownership follows the skin, so another goods ID or a refresh cannot sell it twice. */
    public static boolean canPurchase(Player player, ItemData item, int count) {
        var skin = costumeForItem(item);
        return count == 1 && skin != null && !player.getCostumeList().contains(skin.getId());
    }
}
