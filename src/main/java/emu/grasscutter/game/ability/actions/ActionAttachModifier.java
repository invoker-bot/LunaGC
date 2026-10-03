package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityModifierController;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(AbilityModifierAction.Type.AttachModifier)
public final class ActionAttachModifier extends AbilityActionHandler {

    @Override
    public boolean execute(Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        Grasscutter.getLogger().debug("[Ability] AttachModifier: {}", action.modifierName);

        var modifierData = ability.getData().modifiers.get(action.modifierName);
        if (modifierData == null) {
            Grasscutter.getLogger().debug("Modifier {} not found", action.modifierName);
            return false;
        }

        if ("AllPlayerAvatars".equals(action.target)) {
            return applyToAllPlayerAvatars(ability, action, modifierData, abilityData);
        }

        Grasscutter.getLogger().debug("[Ability] AttachModifier fallback target={}", action.target);

        if (!conditionsPass(ability, action, target)) return true;
        ability.registerModifier(target,
                new AbilityModifierController(ability, ability.getData(), modifierData));
        return true;
    }

    private boolean applyToAllPlayerAvatars(Ability ability, AbilityModifierAction action,
                                             AbilityModifier modifierData, ByteString abilityData) {
        var player = ability.getPlayerOwner();
        if (player == null) return false;

        var team = new java.util.ArrayList<>(player.getTeamManager().getActiveTeam());
        var manager = ability.getManager();
        var seen = new java.util.HashSet<Integer>();
        for (var avatarEntity : team) {
            if (!seen.add(avatarEntity.getId())) continue;
            if (!conditionsPass(ability, action, avatarEntity)) continue;
            ability.registerModifier(avatarEntity,
                    new AbilityModifierController(ability, ability.getData(), modifierData));
            if (modifierData.onAdded != null) for (var a : modifierData.onAdded) {
                manager.executeActionNow(ability, a, abilityData, avatarEntity);
            }
        }
        return true;
    }

}
