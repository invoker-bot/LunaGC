package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.*;
import lombok.Getter;

public class AbilityModifierController {
    @Getter private Ability ability;

    @Getter private AbilityData abilityData;
    @Getter private AbilityModifier modifierData;
    @Getter private final String name;

    public AbilityModifierController(
            Ability ability, AbilityData abilityData, AbilityModifier modifierData) {
        this.ability = ability;
        this.abilityData = abilityData;
        this.modifierData = modifierData;
        this.name = abilityData.modifiers.entrySet().stream()
                .filter(entry -> entry.getValue() == modifierData)
                .map(java.util.Map.Entry::getKey).findFirst().orElse("");
    }
}
