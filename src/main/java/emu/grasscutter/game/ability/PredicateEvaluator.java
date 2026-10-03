package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.data.excels.ProudSkillData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.utils.JsonUtils;
import java.util.List;
import java.util.Map;

public final class PredicateEvaluator {

    private PredicateEvaluator() {}

    public static boolean all(List<Map<String, Object>> predicates, Ability ability,
                               GameEntity owner, GameEntity target, AbilityModifierAction action) {
        if (predicates == null || predicates.isEmpty()) return true;
        for (var pred : predicates) {
            if (pred == null) continue;
            if (!evaluate(pred, ability, owner, target, action)) return false;
        }
        return true;
    }

    public static boolean evaluate(Map<String, Object> pred, Ability ability,
                                    GameEntity owner, GameEntity target, AbilityModifierAction action) {
        Object typeObj = pred.get("$type");
        if (!(typeObj instanceof String type)) return true;
        GameEntity resolved = resolveTarget(pred, ability, owner, target);
        if ("ByUnlockTalentParam".equals(type)) {
            GameEntity caster = ability != null ? ability.getCasterEntity() : null;
            return byUnlockTalentParam(pred, caster != null ? caster : (owner != null ? owner : resolved));
        }
        return switch (type) {
            case "ByHasModifier"      -> byHasModifier(pred, ability, resolved);
            case "ByTargetGlobalValue" -> byTargetGlobalValue(pred, ability, resolved);
            case "ByTargetHPRatio"    -> byTargetHPRatio(pred, ability, resolved);
            default -> true;
        };
    }

    private static GameEntity resolveTarget(Map<String, Object> pred, Ability ability,
                                              GameEntity owner, GameEntity defaultTarget) {
        Object t = pred.get("target");
        if (!(t instanceof String s)) return defaultTarget;
        return switch (s) {
            case "Self", "Target", "Applier" -> defaultTarget;
            case "Owner" -> owner != null ? owner : defaultTarget;
            case "OriginOwner", "CurLocalAvatar" ->
                ability != null && ability.getPlayerOwner() != null
                    ? ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity()
                    : defaultTarget;
            case "Team" ->
                ability != null && ability.getPlayerOwner() != null
                    ? ability.getPlayerOwner().getTeamManager().getEntity()
                    : defaultTarget;
            default -> defaultTarget;
        };
    }

    private static boolean byUnlockTalentParam(Map<String, Object> pred, GameEntity target) {
        Object tp = pred.get("talentParam");
        if (!(tp instanceof String talentParam) || talentParam.isEmpty()) return false;
        if (!(target instanceof EntityAvatar ea)) return false;
        Avatar avatar = ea.getAvatar();
        if (avatar == null) return false;
        if (avatar.getProudSkillList() != null) {
            for (int proudSkillId : avatar.getProudSkillList()) {
                ProudSkillData ps = GameData.getProudSkillDataMap().get(proudSkillId);
                if (ps != null && talentParam.equals(ps.getOpenConfig())) return true;
            }
        }
        if (avatar.getTalentIdList() != null) {
            for (int talentId : avatar.getTalentIdList()) {
                var td = GameData.getAvatarTalentDataMap().get(talentId);
                if (td != null && talentParam.equals(td.getOpenConfig())) return true;
            }
        }
        return false;
    }

    private static boolean byHasModifier(Map<String, Object> pred, Ability ability, GameEntity target) {
        Object mn = pred.get("modifierName");
        if (!(mn instanceof String modifierName) || ability == null || target == null) return false;
        for (var states : target.getAppliedAbilityModifiers().values()) {
            if (states.containsKey(modifierName)) return true;
        }
        for (var modifier : target.getInstancedModifiersSnapshot()) {
            if (modifierName.equals(modifier.getName())) return true;
        }
        return false;
    }

    private static boolean byTargetGlobalValue(Map<String, Object> pred, Ability ability, GameEntity target) {
        if (target == null) return false;
        Object key = pred.get("key");
        if (!(key instanceof String k)) return false;
        float current = target.getGlobalAbilityValues().getOrDefault(k, 0f);
        float bound = readFloat(pred.get("value"), ability);
        Object cmpObj = pred.get("compareType");
        String cmp = cmpObj instanceof String s ? s : "Equal";
        return compare(current, bound, cmp);
    }

    private static boolean compare(float current, float bound, String logic) {
        return switch (logic) {
            case "MoreThan", "Greater"    -> current > bound;
            case "MoreThanAndEqual", "MoreOrEqual", "GreaterOrEqual" -> current >= bound;
            case "LessThan", "Lesser"     -> current < bound;
            case "LessThanAndEqual", "LessAndEqual", "LessOrEqual", "LesserOrEqual" -> current <= bound;
            case "NotEqual"               -> current != bound;
            default                       -> current == bound;
        };
    }

    private static boolean byTargetHPRatio(Map<String, Object> pred, Ability ability, GameEntity target) {
        if (target == null || !pred.containsKey("HPRatio")) return false;
        float maxHp = target.getFightProperty(emu.grasscutter.game.props.FightProperty.FIGHT_PROP_MAX_HP);
        float curHp = target.getFightProperty(emu.grasscutter.game.props.FightProperty.FIGHT_PROP_CUR_HP);
        if (maxHp <= 0f) return false;
        float threshold = readFloat(pred.get("HPRatio"), ability);
        String logic = pred.get("logic") instanceof String value ? value : "Greater";
        return compare(curHp / maxHp, threshold, logic);
    }

    private static float readFloat(Object v, Ability ability) {
        if (v instanceof Number n) return n.floatValue();
        if (v instanceof Map<?, ?> m) {
            Object inner = m.get("value");
            if (inner != null) return readFloat(inner, ability);
            Object exp = m.get("__exp_FixedValue");
            return exp != null ? readFloat(exp, ability) : 0f;
        }
        if (v == null) return 0f;
        var dynamic = JsonUtils.decode(JsonUtils.toJson(v), DynamicFloat.class);
        return ability == null ? dynamic.get() : dynamic.get(AbilityActionHandler.propertiesFor(ability), 0f);
    }
}
