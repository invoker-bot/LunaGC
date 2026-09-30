package emu.grasscutter.game.activity.salesman;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.GameData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import java.util.*;
import java.util.function.*;

/** Preflight every reward before reserving a chance, then acknowledge the actual inventory writes. */
public final class SalesmanRewardDelivery {
    private SalesmanRewardDelivery() {}
    public static int validate(List<ItemParamData> items, IntFunction<ItemData> definitions,
                               Function<ItemType, InventoryTab> tabs, IntUnaryOperator currencies) {
        if (items == null || items.isEmpty()) return Retcode.RET_SVR_ERROR_VALUE;
        var counts = new HashMap<Integer, Long>();
        var newStacks = new HashMap<InventoryTab, Set<Integer>>();
        for (var item : items) {
            if (item == null || item.getId() <= 0 || item.getCount() <= 0) return Retcode.RET_SVR_ERROR_VALUE;
            var definition = definitions.apply(item.getId());
            if (definition == null || definition.getId() != item.getId() || definition.isUseOnGain()) return Retcode.RET_SVR_ERROR_VALUE;
            long count = counts.merge(item.getId(), (long) item.getCount(), Long::sum);
            if (definition.getItemType() == ItemType.ITEM_VIRTUAL) {
                if (item.getId() != 201 && item.getId() != 202) return Retcode.RET_SVR_ERROR_VALUE;
                if ((long) currencies.applyAsInt(item.getId()) + count > Integer.MAX_VALUE) return Retcode.RET_ITEM_EXCEED_LIMIT_VALUE;
            } else {
                if (definition.getItemType() != ItemType.ITEM_MATERIAL || definition.getStackLimit() <= 0
                        || !Set.of(MaterialType.MATERIAL_EXP_FRUIT, MaterialType.MATERIAL_WEAPON_EXP_STONE,
                                MaterialType.MATERIAL_AVATAR_MATERIAL, MaterialType.MATERIAL_TALENT).contains(definition.getMaterialType()))
                    return Retcode.RET_SVR_ERROR_VALUE;
                var tab = tabs.apply(definition.getItemType());
                if (tab == null) return Retcode.RET_SVR_ERROR_VALUE;
                var existing = tab.getItemById(item.getId());
                if (count + (existing == null ? 0 : existing.getCount()) > definition.getStackLimit()) return Retcode.RET_ITEM_EXCEED_LIMIT_VALUE;
                if (existing == null) newStacks.computeIfAbsent(tab, ignored -> new HashSet<>()).add(item.getId());
            }
        }
        return newStacks.entrySet().stream().anyMatch(entry ->
                (long) entry.getKey().getSize() + entry.getValue().size() > entry.getKey().getMaxCapacity())
                ? Retcode.RET_ITEM_EXCEED_LIMIT_VALUE : 0;
    }
    private static int currency(Player player, int id) { return id == 201 ? player.getPrimogems() : player.getMora(); }
    public static int validate(Player player, int rewardId) {
        var reward = GameData.getRewardDataMap().get(rewardId);
        return reward == null ? Retcode.RET_SVR_ERROR_VALUE : validate(reward.getRewardItemList(),
                id -> GameData.getItemDataMap().get(id), player.getInventory()::getInventoryTab, id -> currency(player, id));
    }
    /** Caller holds the inventory monitor from preflight through the grant. */
    public static void grant(Player player, int rewardId) {
        var reward = GameData.getRewardDataMap().get(rewardId);
        if (reward == null || validate(player, rewardId) != 0) throw new IllegalStateException("Salesman reward preflight changed");
        var inventory = player.getInventory();
        for (var param : reward.getRewardItemList()) {
            boolean virtual = GameData.getItemDataMap().get(param.getId()).getItemType() == ItemType.ITEM_VIRTUAL;
            int before = virtual ? currency(player, param.getId()) : inventory.getItemCountById(param.getId());
            if (!inventory.addItem(param, ActionReason.SalesmanReward)) throw new IllegalStateException("Salesman item was not granted");
            int after = virtual ? currency(player, param.getId()) : inventory.getItemCountById(param.getId());
            if ((long) after - before != param.getCount()) throw new IllegalStateException("Salesman grant count changed");
            if (!virtual) DatabaseHelper.saveGameSync(inventory.getItemById(param.getId()));
        }
        // Virtual currencies are part of the player document, rather than item documents.
        DatabaseHelper.saveGameSync(player);
    }
    public static boolean pay(Player player, List<ItemParamData> costs) {
        var inventory = player.getInventory();
        synchronized (inventory) {
            var stacks = costs.stream().map(item -> inventory.getItemById(item.getId())).filter(Objects::nonNull).toList();
            if (!inventory.payItems(costs, 1, ActionReason.SalesmanDeliverItem)) return false;
            for (var item : stacks) DatabaseHelper.saveGameSync(item);
            return true;
        }
    }
}
