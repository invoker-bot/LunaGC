package emu.grasscutter.server.http.handlers;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.reliquary.*;
import emu.grasscutter.data.excels.weapon.WeaponPromoteData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.utils.objects.WeightedList;
import java.util.*;

/** Resource-constrained equipment configuration for the GM form. */
final class GmEquipment {
    static final int MAX_AMOUNT = 100;

    // Mutable DTOs support the project's Gson version, including omitted defaults.
    static final class Request {
        int itemId;
        int target;
        int amount = 1;
        int level = -1;
        Integer promoteLevel;
        int refinement = 1;
        int mainPropId;
        List<Substat> substats = List.of();
    }

    static final class Substat {
        int affixId;
        int rolls;

        Substat(int affixId, int rolls) {
            this.affixId = affixId;
            this.rolls = rolls;
        }
    }

    private static ItemData equipment(int id) {
        var data = GameData.getItemDataMap().get(id);
        require(data != null && data.isEquip(), "请选择有效的武器或圣遗物。");
        return data;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static List<WeaponPromoteData> promotions(ItemData data) {
        return GameData.getWeaponPromoteDataMap().values().stream()
                .filter(p -> p.getWeaponPromoteId() == data.getWeaponPromoteId())
                .sorted(Comparator.comparingInt(WeaponPromoteData::getPromoteLevel))
                .toList();
    }

    static int maxLevel(ItemData data) {
        return data.getItemType() == ItemType.ITEM_RELIQUARY
                ? Math.max(0, data.getMaxLevel() - 1)
                : promotions(data).stream().mapToInt(WeaponPromoteData::getUnlockMaxLevel).max().orElse(1);
    }

    private static int maxRefinement(ItemData data) {
        return data.getSkillAffix() != null
                        && Arrays.stream(data.getSkillAffix()).anyMatch(id -> id > 0)
                ? 5
                : 1;
    }

    private static List<ReliquaryMainPropData> mains(ItemData data) {
        return GameData.getReliquaryMainPropDataMap().values().stream()
                .filter(
                        p ->
                                p.getPropDepotId() == data.getMainPropDepotId()
                                        && p.getWeight() > 0
                                        && p.getFightProp() != null)
                .sorted(Comparator.comparingInt(ReliquaryMainPropData::getId))
                .toList();
    }

    private static List<ReliquaryAffixData> affixes(ItemData data) {
        return GameData.getReliquaryAffixDataMap().values().stream()
                .filter(
                        p ->
                                p.getDepotId() == data.getAppendPropDepotId()
                                        && p.getWeight() > 0
                                        && p.getFightProp() != null)
                .sorted(Comparator.comparingInt(ReliquaryAffixData::getId))
                .toList();
    }

    private static int rollBudget(ItemData data, int displayLevel) {
        int count = data.getAppendPropNum();
        for (int internalLevel = 2; internalLevel <= displayLevel + 1; internalLevel++) {
            if (data.canAddRelicProp(internalLevel)) count++;
        }
        return count;
    }

    static Map<String, Object> options(int itemId) {
        var data = equipment(itemId);
        var result = new LinkedHashMap<String, Object>();
        result.put("itemId", itemId);
        result.put("type", data.getItemType().name());
        result.put("rankLevel", data.getRankLevel());
        result.put("maxLevel", maxLevel(data));
        result.put("maxAmount", MAX_AMOUNT);
        if (data.getItemType() == ItemType.ITEM_WEAPON) {
            result.put("minLevel", 1);
            result.put("maxRefinement", maxRefinement(data));
            var rows = new ArrayList<Map<String, Object>>();
            int previousCap = 1;
            for (var p : promotions(data)) {
                rows.add(
                        Map.of(
                                "level",
                                p.getPromoteLevel(),
                                "minLevel",
                                previousCap,
                                "maxLevel",
                                p.getUnlockMaxLevel()));
                previousCap = p.getUnlockMaxLevel();
            }
            result.put("promotions", rows);
            result.put(
                    "weaponStats",
                    data.getWeaponProperties() == null
                            ? List.of()
                            : Arrays.stream(data.getWeaponProperties())
                                    .map(p -> label(p.getPropType()))
                                    .toList());
        } else {
            result.put("minLevel", 0);
            result.put(
                    "slot",
                    switch (data.getEquipType()) {
                        case EQUIP_BRACER -> "生之花";
                        case EQUIP_NECKLACE -> "死之羽";
                        case EQUIP_SHOES -> "时之沙";
                        case EQUIP_RING -> "空之杯";
                        case EQUIP_DRESS -> "理之冠";
                        default -> data.getEquipType().name();
                    });
            result.put("initialSubstats", data.getAppendPropNum());
            var budgets = new ArrayList<Integer>();
            for (int level = 0; level <= maxLevel(data); level++) budgets.add(rollBudget(data, level));
            result.put("rollBudgets", budgets);
            result.put(
                    "mainProps",
                    mains(data).stream()
                            .map(
                                    p -> {
                                        var values = new ArrayList<String>();
                                        for (int level = 1; level <= data.getMaxLevel(); level++) {
                                            var row = GameData.getRelicLevelData(data.getRankLevel(), level);
                                            values.add(
                                                    row == null
                                                            ? ""
                                                            : value(p.getFightProp(), row.getPropValue(p.getFightProp())));
                                        }
                                        return Map.of(
                                                "id",
                                                p.getId(),
                                                "property",
                                                p.getFightProp().name(),
                                                "label",
                                                label(p.getFightProp()),
                                                "values",
                                                values);
                                    })
                            .toList());
            result.put(
                    "affixes",
                    affixes(data).stream()
                            .map(
                                    p ->
                                            Map.of(
                                                    "id",
                                                    p.getId(),
                                                    "property",
                                                    p.getFightProp().name(),
                                                    "label",
                                                    label(p.getFightProp())
                                                            + " +"
                                                            + value(p.getFightProp(), p.getPropValue())))
                            .toList());
        }
        return result;
    }

    static List<GameItem> create(Request request) {
        require(request != null, "缺少装备参数。");
        var data = equipment(request.itemId);
        require(request.amount >= 1 && request.amount <= MAX_AMOUNT, "装备数量应为 1–100。");
        require(
                request.level >= (data.getItemType() == ItemType.ITEM_WEAPON ? 1 : 0)
                        && request.level <= maxLevel(data),
                "等级超出该装备的资源上限。");
        var subs = request.substats == null ? List.<Substat>of() : request.substats;
        if (data.getItemType() == ItemType.ITEM_WEAPON) {
            require(request.mainPropId == 0 && subs.isEmpty(), "武器属性由资源计算，不能指定圣遗物词条。");
            require(
                    request.refinement >= 1 && request.refinement <= maxRefinement(data), "精炼等级超出该武器的上限。");
            var stages = promotions(data);
            require(!stages.isEmpty(), "缺少该武器的突破资源。");
            int stage =
                    request.promoteLevel == null
                            ? stages.stream()
                                    .filter(p -> p.getUnlockMaxLevel() >= request.level)
                                    .findFirst()
                                    .orElseThrow()
                                    .getPromoteLevel()
                            : request.promoteLevel;
            int lower = 1;
            boolean valid = false;
            for (var p : stages) {
                if (p.getPromoteLevel() == stage
                        && request.level >= lower
                        && request.level <= p.getUnlockMaxLevel()) valid = true;
                lower = p.getUnlockMaxLevel();
            }
            require(valid, "所选突破阶段与武器等级不匹配。");
            var items = new ArrayList<GameItem>();
            for (int i = 0; i < request.amount; i++) {
                var item = new GameItem(data);
                item.setLevel(request.level);
                item.setPromoteLevel(stage);
                item.setRefinement(request.refinement - 1);
                int exp = 0;
                for (int level = 1; level < request.level; level++)
                    exp += GameData.getWeaponExpRequired(data.getRankLevel(), level);
                item.setTotalExp(exp);
                items.add(item);
            }
            return items;
        }
        require(request.promoteLevel == null && request.refinement == 1, "圣遗物没有突破或精炼等级。");
        require(subs.size() <= 4, "最多选择四种副属性。");
        var pool = affixes(data);
        var chosen = new ArrayList<ReliquaryAffixData>();
        var used = new HashSet<FightProperty>();
        int customRolls = 0;
        int budget = rollBudget(data, request.level);
        for (var sub : subs) {
            require(sub != null && sub.rolls >= 1 && sub.rolls <= budget, "副属性次数超出当前等级允许的次数。");
            var affix = pool.stream().filter(p -> p.getId() == sub.affixId).findFirst().orElse(null);
            require(affix != null && (sub.rolls == 1 || affix.getUpgradeWeight() > 0), "副属性不属于该圣遗物的属性库。");
            require(used.add(affix.getFightProp()), "副属性不能重复选择同一种属性。");
            chosen.add(affix);
            customRolls += sub.rolls;
        }
        int distinct = Math.min(4, budget);
        require(customRolls + Math.max(0, distinct - chosen.size()) <= budget, "所选次数没有为其余副属性留下位置。");
        var mainPool =
                mains(data).stream()
                        .filter(
                                p ->
                                        (request.mainPropId == 0 || p.getId() == request.mainPropId)
                                                && !used.contains(p.getFightProp()))
                        .toList();
        require(!mainPool.isEmpty(), "主属性不适用于该部位，或与所选副属性重复。");
        var items = new ArrayList<GameItem>();
        for (int i = 0; i < request.amount; i++) {
            var weights = new WeightedList<ReliquaryMainPropData>();
            mainPool.forEach(p -> weights.add(p.getWeight(), p));
            var main = weights.next();
            var item = new GameItem(data);
            item.setMainPropId(main.getId());
            item.setLevel(request.level + 1); // Client +0 is stored as 1.
            int exp = 0;
            for (int level = 1; level <= request.level; level++)
                exp += GameData.getRelicExpRequired(data.getRankLevel(), level);
            item.setTotalExp(exp);
            var ids = item.getAppendPropIdList();
            ids.clear();
            var properties = new HashSet<>(used);
            chosen.forEach(p -> ids.add(p.getId()));
            while (ids.size() < distinct) {
                var next = randomAffix(pool, main.getFightProp(), properties, false);
                ids.add(next.getId());
                properties.add(next.getFightProp());
            }
            for (int s = 0; s < chosen.size(); s++) {
                for (int n = 1; n < subs.get(s).rolls; n++) ids.add(chosen.get(s).getId());
            }
            while (ids.size() < budget)
                ids.add(randomAffix(pool, main.getFightProp(), properties, true).getId());
            items.add(item);
        }
        return items;
    }

    private static ReliquaryAffixData randomAffix(
            List<ReliquaryAffixData> pool,
            FightProperty main,
            Set<FightProperty> existing,
            boolean upgrade) {
        var weights = new WeightedList<ReliquaryAffixData>();
        for (var p : pool) {
            if (p.getFightProp() != main && existing.contains(p.getFightProp()) == upgrade)
                weights.add(upgrade ? p.getUpgradeWeight() : p.getWeight(), p);
        }
        require(weights.size() > 0, "缺少可用的副属性资源。");
        return weights.next();
    }

    /** Returns success only after inventory insertion and acknowledged item saves. */
    static int grant(Player player, List<GameItem> items) {
        var inventory = player.getInventory();
        synchronized (inventory) {
            var tab = inventory.getInventoryTab(items.get(0).getItemType());
            require(tab != null && tab.getMaxCapacity() - tab.getSize() >= items.size(), "装备背包空间不足。");
            int granted = 0;
            for (var item : items) {
                if (!inventory.addItem(item, ActionReason.SubfieldDrop))
                    throw new IllegalStateException("发放中断，已加入背包 " + granted + " 件；请先检查背包。");
                granted++;
                try {
                    DatabaseHelper.saveGameSync(item);
                } catch (Exception e) {
                    throw new IllegalStateException("已加入背包 " + granted + " 件，但保存未确认；请检查背包和服务端日志，勿重复发放。", e);
                }
            }
            return granted;
        }
    }

    private static String label(FightProperty property) {
        if (property == null) return "未知属性";
        return switch (property) {
            case FIGHT_PROP_BASE_ATTACK, FIGHT_PROP_ATTACK -> "攻击力";
            case FIGHT_PROP_HP -> "生命值";
            case FIGHT_PROP_HP_PERCENT -> "生命值百分比";
            case FIGHT_PROP_ATTACK_PERCENT -> "攻击力百分比";
            case FIGHT_PROP_DEFENSE -> "防御力";
            case FIGHT_PROP_DEFENSE_PERCENT -> "防御力百分比";
            case FIGHT_PROP_CRITICAL -> "暴击率";
            case FIGHT_PROP_CRITICAL_HURT -> "暴击伤害";
            case FIGHT_PROP_CHARGE_EFFICIENCY -> "元素充能效率";
            case FIGHT_PROP_ELEMENT_MASTERY -> "元素精通";
            case FIGHT_PROP_HEAL_ADD -> "治疗加成";
            case FIGHT_PROP_PHYSICAL_ADD_HURT -> "物理伤害加成";
            case FIGHT_PROP_FIRE_ADD_HURT -> "火元素伤害加成";
            case FIGHT_PROP_ELEC_ADD_HURT -> "雷元素伤害加成";
            case FIGHT_PROP_WATER_ADD_HURT -> "水元素伤害加成";
            case FIGHT_PROP_GRASS_ADD_HURT -> "草元素伤害加成";
            case FIGHT_PROP_WIND_ADD_HURT -> "风元素伤害加成";
            case FIGHT_PROP_ROCK_ADD_HURT -> "岩元素伤害加成";
            case FIGHT_PROP_ICE_ADD_HURT -> "冰元素伤害加成";
            default -> property.name();
        };
    }

    private static String value(FightProperty property, float value) {
        boolean flat =
                property == FightProperty.FIGHT_PROP_HP
                        || property == FightProperty.FIGHT_PROP_ATTACK
                        || property == FightProperty.FIGHT_PROP_BASE_ATTACK
                        || property == FightProperty.FIGHT_PROP_DEFENSE
                        || property == FightProperty.FIGHT_PROP_ELEMENT_MASTERY;
        return String.format(Locale.ROOT, flat ? "%.1f" : "%.1f%%", flat ? value : value * 100);
    }
}
