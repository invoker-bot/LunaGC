package emu.grasscutter.game.props.ItemUseAction;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.props.*;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;

public class ItemUseOpenRandomChest extends ItemUseInt {
    public ItemUseOpenRandomChest(String[] useParam) {
        super(useParam);
    }

    @Override
    public ItemUseOp getItemUseOp() {
        return ItemUseOp.ITEM_USE_OPEN_RANDOM_CHEST;
    }

    @Override
    public boolean useItem(UseItemParams params) { // cash shop material bundles
        var data = params.player.getServer().getShopSystem().getShopChestData(this.i);
        if (data == null) {
            // ShopChest.v2.json has no entry for this chest id, so the item has no rewards to
            // hand out. Name the id -- otherwise this only surfaces as a generic "item use
            // failed" and the missing data key is impossible to find.
            Grasscutter.getLogger().warn(
                    "Item {} use failed: chest id {} has no ShopChest.v2.json entry.",
                    params.usedItemId, this.i);
            return false;
        }
        var rewardItems = new ArrayList<GameItem>();
        var reliquaryItems = new ArrayList<ItemParamData>();

        for (var itemParamData : data) {
            var itemData = GameData.getItemDataMap().getOrDefault(itemParamData.getItemId(), null);
            if (itemData == null) {
                Grasscutter.getLogger().warn(
                        "Chest {} rewards item {} which is not in the item data map; skipping.",
                        this.i, itemParamData.getItemId());
            } else if (Objects.requireNonNull(itemData.getItemType()) == ItemType.ITEM_RELIQUARY) {
                reliquaryItems.add(itemParamData);
            } else {
                rewardItems.add(new GameItem(itemParamData));
            }
        }

        if (!reliquaryItems.isEmpty()) {
            rewardItems.add(new GameItem(reliquaryItems.get(new Random().nextInt(reliquaryItems.size()))));
        }


        if (!rewardItems.isEmpty()) {
            params.player.getInventory().addItems(rewardItems, ActionReason.Shop);
        }
        return true;
    }
}
