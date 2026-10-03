package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.ActionLoseHP;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import org.junit.jupiter.api.*;

class AbilityHealthCostTest {
    private AbilityTestFixture fixture;
    private Ability ability;

    @BeforeEach
    void scene() throws Exception {
        fixture = new AbilityTestFixture();
        ability = new Ability(JsonUtils.decode("{\"abilityName\":\"Test_HealthCost\"}", AbilityData.class),
                fixture.entity, fixture.player);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 200);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
    }

    @AfterEach
    void restore() { fixture.close(); }

    private float lose(String fields) {
        var action = JsonUtils.decode("{\"$type\":\"LoseHP\"," + fields + "}", AbilityModifierAction.class);
        assertTrue(new ActionLoseHP().execute(ability, action, ByteString.EMPTY, fixture.entity));
        return fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
    }

    @Test
    void percentageOfCurrentHealthUsesCurrentRatherThanMaximumHealth() {
        assertEquals(75f, lose("\"amountByTargetCurrentHPRatio\":0.25"));
    }

    @Test
    void percentageOfMaximumHealthStillUsesMaximumHealth() {
        assertEquals(50f, lose("\"amountByTargetMaxHPRatio\":0.25"));
    }

    @Test
    void bothCostsCanBeCombinedWithoutChangingTheirBases() {
        assertEquals(55f, lose("\"amountByTargetCurrentHPRatio\":0.25,\"amountByTargetMaxHPRatio\":0.1"));
    }

    @Test
    void healthFloorStillLimitsTheCost() {
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 110);
        assertEquals(100f, lose("\"amountByTargetCurrentHPRatio\":0.2,\"limboByTargetMaxHPRatio\":0.5"));
    }

    @Test
    void furinasActualEnhancedAttackCostUsesCurrentHealth() throws Exception {
        var config = JsonUtils.loadToList(Path.of(
                "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Furina.json"), AbilityConfigData.class);
        var data = config.stream().map(row -> row.Default)
                .filter(row -> row.abilityName.equals("Avatar_Furina_Constellation_2")).findFirst().orElseThrow();
        ability = new Ability(data, fixture.entity, fixture.player);
        ability.getAbilitySpecials().put("PreAttack_LoseHP", 0.2f);
        fixture.player.setSceneLoadState(Player.SceneLoadState.LOADED);
        var action = data.modifiers.get("Furina_WaterEnhancedMode_Ousia_LoseHP").onAdded[0];
        assertTrue(new ActionLoseHP().execute(ability, action, ByteString.EMPTY, fixture.entity));
        assertEquals(80f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void aHealthCostDoesNotRunWhenItsConditionFails() {
        assertEquals(100f, lose("""
                "amount":20,"predicates":[{"$type":"ByTargetHPRatio","HPRatio":0.7}]
                """));
    }
}
