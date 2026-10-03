package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import java.util.List;

@AbilityAction(AbilityModifierAction.Type.ReviveAvatar)
public final class ActionReviveAvatar extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        var player = ability.getPlayerOwner();
        if (player == null) return false;

        float ratio = action.amountByTargetMaxHPRatio.get(propertiesFor(ability), 0f);
        if (!Float.isFinite(ratio) || ratio <= 0f) return true;

        List<EntityAvatar> recipients;
        if ("AllPlayerAvatars".equals(action.target) || "CurTeamAvatars".equals(action.target)) {
            recipients = List.copyOf(player.getTeamManager().getActiveTeam());
        } else {
            // Reading the current avatar on an empty team otherwise creates an unrelated avatar.
            if (("CurLocalAvatar".equals(action.target) || "OriginOwner".equals(action.target))
                    && player.getTeamManager().getActiveTeam().isEmpty()) return true;
            var recipient = resolveTarget(ability, target, action.target);
            recipients = recipient instanceof EntityAvatar avatar ? List.of(avatar) : List.of();
        }

        for (var avatar : recipients) {
            if (conditionsPass(ability, action, avatar)) {
                avatar.revive(avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP) * ratio);
            }
        }
        return true;
    }
}
