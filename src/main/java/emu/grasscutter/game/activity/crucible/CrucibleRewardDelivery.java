package emu.grasscutter.game.activity.crucible;

import emu.grasscutter.game.activity.crucible.CrucibleRewards.Result;
import emu.grasscutter.game.inventory.*;
import java.util.*;
import java.util.function.Function;

/** Preflight the archived Crucible's EXP/book drops before spending resin. */
public final class CrucibleRewardDelivery {
    private CrucibleRewardDelivery() {}
    public static Result validate(List<GameItem> items, Function<ItemType, InventoryTab> tabs) {
        if (items == null || items.isEmpty()) return Result.INVALID_REWARD;
        var counts = new HashMap<Integer, Long>();
        var newStacks = new HashMap<InventoryTab, Set<Integer>>();
        for (var item : items) {
            if (item == null || item.getItemData() == null || item.getCount() <= 0
                    || item.getItemId() != item.getItemData().getId()) return Result.INVALID_REWARD;
            var data = item.getItemData();
            long count = counts.merge(item.getItemId(), (long) item.getCount(), Long::sum);
            if (count > Integer.MAX_VALUE || data.isUseOnGain()) return Result.INVALID_REWARD;
            if (data.getItemType() == ItemType.ITEM_VIRTUAL) {
                if (item.getItemId() != 102 && item.getItemId() != 105) return Result.INVALID_REWARD;
                continue;
            }
            if (data.getItemType() != ItemType.ITEM_MATERIAL
                    || data.getMaterialType() != MaterialType.MATERIAL_EXP_FRUIT) return Result.INVALID_REWARD;
            var tab = tabs.apply(data.getItemType());
            if (tab == null || data.getStackLimit() <= 0) return Result.INVALID_REWARD;
            var existing = tab.getItemById(item.getItemId());
            if (count + (existing == null ? 0 : existing.getCount()) > data.getStackLimit())
                return Result.INVENTORY_FULL;
            if (existing == null) newStacks.computeIfAbsent(tab, ignored -> new HashSet<>()).add(item.getItemId());
        }
        return newStacks.entrySet().stream().anyMatch(entry ->
                (long) entry.getKey().getSize() + entry.getValue().size() > entry.getKey().getMaxCapacity())
                ? Result.INVENTORY_FULL : Result.OK;
    }
}
