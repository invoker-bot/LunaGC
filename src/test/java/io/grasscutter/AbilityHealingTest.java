package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.ActionHealHP;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import org.junit.jupiter.api.*;

class AbilityHealingTest {
    private AbilityTestFixture fixture;
    private Ability ability;

    @BeforeEach
    void scene() throws Exception {
        fixture = new AbilityTestFixture();
        ability = new Ability(JsonUtils.decode("{\"abilityName\":\"Test_Healing\"}", AbilityData.class),
                fixture.entity, fixture.player);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 200);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
    }

    @AfterEach
    void restore() { fixture.close(); }

    private float heal(String fields) {
        var action = JsonUtils.decode("{\"$type\":\"HealHP\"," + fields + "}", AbilityModifierAction.class);
        assertTrue(new ActionHealHP().execute(ability, action, ByteString.EMPTY, fixture.entity));
        return fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
    }

    @Test
    void aFlatHealStillAddsItsAmount() {
        assertEquals(110f, heal("\"amount\":10"));
    }

    @Test
    void combinedFlatAndPercentageHealDoesNotAddTheFlatPartTwice() {
        assertEquals(120f, heal("\"amount\":10,\"amountByCasterMaxHPRatio\":0.05"));
    }

    @Test
    void healingBasedOnCurrentTargetHealthUsesCurrentHealth() {
        assertEquals(110f, heal("\"amountByTargetCurrentHPRatio\":0.1"));
    }

    @Test
    void obfuscatedHealthRatiosFromTheResourcesContributeToHealing() {
        assertEquals(130f, heal("\"GJBFAJMJFOP\":0.05,\"BJEKIJMNDAA\":0.1,\"FPOCDLCHDPE\":0.05"));
    }

    @Test
    void aHealingActionDoesNotRunWhenItsConditionFails() {
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 71);
        assertEquals(71f, heal("""
                "amount":10,"predicates":[{"$type":"ByTargetHPRatio","HPRatio":0.7,"logic":"LesserOrEqual"}]
                """));
    }

    @Test
    void bennettsActualCallbackUsesItsConditionAndAddsTheFlatPartOnce() throws Exception {
        var config = JsonUtils.loadToList(Path.of(
                "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Bennett.json"), AbilityConfigData.class);
        var data = config.stream().map(row -> row.Default)
                .filter(row -> row.abilityName.equals("Avatar_Bennett_ElementalBurst_Gadget")).findFirst().orElseThrow();
        ability = new Ability(data, fixture.entity, fixture.player);
        ability.getAbilitySpecials().put("HealConst", 10f);
        ability.getAbilitySpecials().put("HealMaxHpRatio", 0.05f);
        var target = new AbilityTestFixture.TestEntity(fixture.scene);
        target.setId(43);
        fixture.scene.getEntities().put(43, target);
        target.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 70);
        var action = data.modifiers.get("WarmField_Handler").onThinkInterval[2];
        var handler = new ActionHealHP();
        assertTrue(handler.execute(ability, action, ByteString.EMPTY, target));
        assertEquals(90f, target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 71);
        assertTrue(handler.execute(ability, action, ByteString.EMPTY, target));
        assertEquals(71f, target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }
}
