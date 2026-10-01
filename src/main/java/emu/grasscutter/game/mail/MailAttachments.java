package emu.grasscutter.game.mail;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.inventory.*;
import java.util.*;

public final class MailAttachments {
    private MailAttachments() {}

    public static void validate(List<Mail.MailItem> items) {
        if (items == null || items.size() > 10) throw new IllegalArgumentException("附件最多 10 种物品。");
        var ids = new HashSet<Integer>();
        for (var a : items) {
            var data = a == null ? null : GameData.getItemDataMap().get(a.itemId);
            if (data == null || data.getItemType() == null || !ids.add(a.itemId))
                throw new IllegalArgumentException("附件物品不存在或重复。");
            int maxLevel =
                    switch (data.getItemType()) {
                        case ITEM_WEAPON -> GameData.getWeaponPromoteDataMap().values().stream()
                                .filter(p -> p.getWeaponPromoteId() == data.getWeaponPromoteId())
                                .mapToInt(p -> p.getUnlockMaxLevel())
                                .max()
                                .orElse(90);
                        case ITEM_RELIQUARY -> Math.max(0, data.getMaxLevel() - 1);
                        default -> 1;
                    };
            int maxCount =
                    data.getItemType() == ItemType.ITEM_WEAPON
                                    || data.getItemType() == ItemType.ITEM_RELIQUARY
                            ? 100
                            : 1000000;
            int minLevel = data.getItemType() == ItemType.ITEM_RELIQUARY ? 0 : 1;
            if (a.itemCount < 1
                    || a.itemCount > maxCount
                    || a.itemLevel < minLevel
                    || a.itemLevel > maxLevel) throw new IllegalArgumentException("附件数量或等级超出物品允许范围。");
            if (data.getItemType() == ItemType.ITEM_VIRTUAL
                    && !Set.of(201, 202, 203, 204).contains(a.itemId))
                throw new IllegalArgumentException("邮件目前支持原石、摩拉、结晶和洞天宝钱；经验等即时效果请使用给予功能。");
            if (!Set.of(
                            ItemType.ITEM_VIRTUAL,
                            ItemType.ITEM_MATERIAL,
                            ItemType.ITEM_WEAPON,
                            ItemType.ITEM_RELIQUARY,
                            ItemType.ITEM_FURNITURE)
                    .contains(data.getItemType())) throw new IllegalArgumentException("该类型暂不支持邮件附件。");
            if (data.getMaterialType() != null
                    && Set.of(
                                    MaterialType.MATERIAL_AVATAR,
                                    MaterialType.MATERIAL_FLYCLOAK,
                                    MaterialType.MATERIAL_COSTUME,
                                    MaterialType.MATERIAL_NAMECARD)
                            .contains(data.getMaterialType()))
                throw new IllegalArgumentException("角色与外观请通过给予功能发送。");
        }
    }

    public static void checkCapacity(Inventory inventory, List<Mail.MailItem> items) {
        var slots = new HashMap<InventoryTab, Integer>();
        for (var a : items) {
            var data = GameData.getItemDataMap().get(a.itemId);
            var tab = inventory.getInventoryTab(data.getItemType());
            if (data.getItemType() == ItemType.ITEM_VIRTUAL) {
                int current =
                        switch (a.itemId) {
                            case 201 -> inventory.getPlayer().getPrimogems();
                            case 202 -> inventory.getPlayer().getMora();
                            case 203 -> inventory.getPlayer().getCrystals();
                            case 204 -> inventory.getPlayer().getHomeCoin();
                            default -> 0;
                        };
                if ((long) current + a.itemCount > Integer.MAX_VALUE)
                    throw new IllegalArgumentException("货币数量超出上限。");
                continue;
            }
            if (tab == null) throw new IllegalArgumentException("背包不支持该附件。");
            boolean equipment =
                    data.getItemType() == ItemType.ITEM_WEAPON
                            || data.getItemType() == ItemType.ITEM_RELIQUARY;
            var existing = tab.getItemById(a.itemId);
            if (!equipment
                    && (long) (existing == null ? 0 : existing.getCount()) + a.itemCount
                            > data.getStackLimit()) throw new IllegalArgumentException("物品堆叠已达到上限。");
            slots.merge(tab, equipment ? a.itemCount : existing == null ? 1 : 0, Integer::sum);
        }
        slots.forEach(
                (tab, extra) -> {
                    if ((long) tab.getSize() + extra > tab.getMaxCapacity())
                        throw new IllegalArgumentException("背包空间不足，请先清理背包。");
                });
    }
}
