package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityModifierController;
import emu.grasscutter.game.ability.PredicateEvaluator;
import emu.grasscutter.game.entity.GameEntity;
import java.util.List;
import java.util.Map;

@AbilityAction(AbilityModifierAction.Type.ApplyModifier)
public final class ActionApplyModifier extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (action.predicates != null && !action.predicates.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> preds = (List<Map<String, Object>>) (List<?>) action.predicates;
            if (!PredicateEvaluator.all(preds, ability, ability.getOwner(), target, action)) return true;
        }
        var modifierData = ability.getData().modifiers.get(action.modifierName);
        if (modifierData == null) return false;

        if ("Unique".equals(modifierData.stacking)) {
            ability.removeModifier(target, action.modifierName);
        }

        AbilityModifierController modifier = new AbilityModifierController(ability, ability.getData(), modifierData);
        ability.registerModifier(target, modifier);
        var manager = ability.getManager();
        if (modifierData.onAdded != null)
            for (var a : modifierData.onAdded)
                manager.executeActionNow(ability, a, abilityData, target);
        // onAttackLanded belongs to an actual hit invocation. Applying the listener must not
        // grant energy, add a mark or trigger a reaction before an attack has happened.

        return true;
    }
}
