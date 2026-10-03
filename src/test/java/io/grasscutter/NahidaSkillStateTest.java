package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Regression coverage using Nahida's actual 7.1 resource configuration. */
class NahidaSkillStateTest {
    private List<AbilityConfigData> configs;
    private AbilityTestFixture fixture;
    private AbilityTestFixture.TestPlayer player;
    private AbilityTestFixture.TestEntity entity;

    @BeforeEach
    void configuration() throws Exception {
        configs = JsonUtils.loadToList(
                Path.of("resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Nahida.json"),
                AbilityConfigData.class);
        fixture = new AbilityTestFixture();
        player = fixture.player;
        entity = fixture.entity;
    }

    @AfterEach
    void restore() {
        fixture.close();
    }

    private AbilityData ability(String suffix) {
        var data = configs.stream()
                .map(c -> c.Default)
                .filter(c -> c.abilityName.equals("Avatar_Nahida_" + suffix))
                .findFirst().orElseThrow();
        data.initialize();
        return data;
    }

    // ModifierChange and server actions must resolve the very same modifier index.
    private void assertModifier(AbilityData ability, int index, String name) throws Exception {
        var oldData = GameData.getAbilityDataMap().put(ability.abilityName, ability);
        try {
            var change = AbilityMetaModifierChange.newBuilder()
                    .setAction(ModifierAction.MODIFIER_ACTION_ADDED)
                    .setParentAbilityName(AbilityString.newBuilder().setStr(ability.abilityName))
                    .setModifierLocalId(index).build();
            var invoke = AbilityInvokeEntry.newBuilder().setEntityId(42)
                    .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                    .setHead(AbilityInvokeEntryHead.newBuilder()
                            .setInstancedAbilityId(1).setInstancedModifierId(100))
                    .setAbilityData(change.toByteString()).build();
            player.getAbilityManager().onAbilityInvoke(invoke);
        } finally {
            if (oldData == null) GameData.getAbilityDataMap().remove(ability.abilityName);
            else GameData.getAbilityDataMap().put(ability.abilityName, oldData);
        }
        var controller = entity.getInstancedModifiers().get(100);
        assertNotNull(controller, "Client modifier must remain registered until removed");
        var actual = controller.getModifierData();
        assertSame(ability.modifiers.get(name), actual, "Wrong modifier for client index " + index);
        var removal = AbilityMetaModifierChange.newBuilder()
                .setAction(ModifierAction.MODIFIER_ACTION_REMOVED).build();
        player.getAbilityManager().onAbilityInvoke(AbilityInvokeEntry.newBuilder().setEntityId(42)
                .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                .setHead(AbilityInvokeEntryHead.newBuilder().setInstancedModifierId(100))
                .setAbilityData(removal.toByteString()).build());
        assertFalse(entity.getInstancedModifiers().containsKey(100), "Release must clear the same modifier");
    }

    @Test
    void pressingElementalSkillSelectsHoldButtonInsteadOfTheMonsterMark() throws Exception {
        assertModifier(ability("ElementalArt"), 5, "Avatar_Nahida_ElementalArt_HoldButton");
    }

    @Test
    void aimingSelectsTheRayCastHandlerInsteadOfTheHitBoxOffset() throws Exception {
        assertModifier(ability("ElementalArt_RayCast"), 5, "Avatar_Nahida_RayCast_Handler");
    }

    @Test
    void focusUsesTheModifierThatExitsFocusWhenReleased() throws Exception {
        var ability = ability("ElementalArt_RayCast");
        assertModifier(ability, 10, "Focus");
        var focus = ability.modifiers.get("Focus");
        assertEquals(AbilityModifierAction.Type.AvatarExitFocus, focus.onRemoved[0].type);
        assertSame(focus.onRemoved[0], ability.localIdToAction.get(3 + (10 << 3) + (1 << 9) + (1 << 15)));
    }

    @Test
    void releasingSelectsTheRayCastTriggerInsteadOfTheCameraEffect() throws Exception {
        assertModifier(ability("ElementalArt_RayCast"), 7, "Avatar_Nahida_RayCast_Trigger");
    }

    @Test
    void shortPressSelectsStrikeAndStillStartsTheCorrectSkill() throws Exception {
        var ability = ability("ElementalArt_Click");
        assertModifier(ability, 1, "Avatar_Nahida_ElementalArt_Click_Strike");
        var start = ability.modifiers.get("Avatar_Nahida_ElementalArt_Click_Strike").onAdded[0];
        assertEquals(10732, start.skillID);
        assertSame(start, ability.localIdToAction.get(3 + (1 << 3) + (1 << 15)));
    }

}
