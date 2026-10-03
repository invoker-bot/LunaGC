package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.PredicateEvaluator;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import java.util.List;
import java.util.Map;

@AbilityAction(AbilityModifierAction.Type.AddGlobalValue)
public final class ActionAddGlobalValue extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (action.predicates != null && !action.predicates.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> preds = (List<Map<String, Object>>) (List<?>) action.predicates;
            if (!PredicateEvaluator.all(preds, ability, ability.getOwner(), target, action)) return true;
        }
        var properties = propertiesFor(ability);
        String valueKey = action.key;
        float valueToAdd = action.ratio.get(properties, 0f);
        float maxValue = action.maxValue.get(properties, 0f);
        float minValue = action.minValue.get(properties, 0f);

        // Hit actions can arrive on different ability workers. Updating within compute avoids
        // losing an increment when both workers read the same previous value.
        float newValue = target.getGlobalAbilityValues().compute(valueKey, (key, current) -> {
            float updated = (current == null ? 0f : current) + valueToAdd;
            return action.useLimitRange ? Math.max(minValue, Math.min(maxValue, updated)) : updated;
        });

        target.onAbilityValueUpdate();
        if (!AbilityManager.isServerOwnedChain()) {
            var scene = target.getScene();
            var host = scene == null ? null : scene.getHost();
            if (host != null) {
                host.sendPacket(new PacketServerGlobalValueChangeNotify(target, valueKey, newValue));
            }
        }

        return true;
    }
}
