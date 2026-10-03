package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;

/**
 * Writes an ability's global value.
 *
 * <p>Both versions honor the configured min/max range only when useLimitRange is enabled.
 * Unbounded writes retain their value even though the absent bounds default to zero.
 */
@AbilityAction({
    AbilityModifierAction.Type.SetGlobalValue,
    AbilityModifierAction.Type.SetGlobalValueV2
})
public final class ActionSetGlobalValue extends AbilityActionHandler {

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        var properties = propertiesFor(ability);
        var valueKey = action.key;
        float computedValue = action.writtenValue().get(properties, 0f);
        if (action.useLimitRange) {
            computedValue = Math.max(action.minValue.get(properties, 0f),
                    Math.min(action.maxValue.get(properties, 0f), computedValue));
        }
        target.getGlobalAbilityValues().put(valueKey, computedValue);
        target.onAbilityValueUpdate();

        // Team abilities can run against a pseudo-entity with no scene.
        if (!AbilityManager.isServerOwnedChain()) {
            var scene = target.getScene();
            var host = scene == null ? null : scene.getHost();
            if (host != null) {
                host.sendPacket(new PacketServerGlobalValueChangeNotify(target, valueKey, computedValue));
            }
        }
        return true;
    }
}
