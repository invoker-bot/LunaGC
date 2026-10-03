package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;

class AbilityPredicateTest {
    private AbilityTestFixture fixture;
    private Ability ability;

    @BeforeEach
    void scene() throws Exception {
        fixture = new AbilityTestFixture();
        ability = new Ability(JsonUtils.decode("""
                {"abilityName":"Test_Predicate","abilitySpecials":{"threshold":0.5,"countLimit":3},
                 "modifiers":{"Buff":{},"Blocked":{}}}
                """, AbilityData.class), fixture.entity, fixture.player);
        hp(fixture.entity, 100, 50);
    }

    @AfterEach
    void restore() { fixture.close(); }

    private void hp(GameEntity target, float max, float current) {
        target.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, max);
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, current);
    }

    private boolean check(String json, GameEntity target) {
        @SuppressWarnings("unchecked")
        var predicate = (Map<String, Object>) JsonUtils.decode(json, Map.class);
        return PredicateEvaluator.evaluate(predicate, ability, fixture.entity, target, null);
    }

    private boolean ratio(Object threshold, String logic, GameEntity target) {
        var predicate = new HashMap<String, Object>();
        predicate.put("$type", "ByTargetHPRatio");
        predicate.put("HPRatio", threshold);
        if (logic != null) predicate.put("logic", logic);
        return PredicateEvaluator.evaluate(predicate, ability, fixture.entity, target, null);
    }

    private EntityAvatar member(float hp) {
        var avatar = new Avatar() {
            @Override public Player getPlayer() { return fixture.player; }
        };
        var member = new EntityAvatar(fixture.scene, avatar);
        hp(member, 100, hp);
        fixture.player.getTeamManager().getActiveTeam().add(member);
        return member;
    }

    private AbilityModifierAction action(String json) {
        return Objects.requireNonNull(JsonUtils.decode(json, AbilityModifierAction.class));
    }

    private boolean hasBuff(GameEntity entity) {
        return PredicateEvaluator.evaluate(Map.of("$type", "ByHasModifier", "modifierName", "Buff"),
                ability, fixture.entity, entity, null);
    }

    @Test
    void numericHealthThresholdHonorsTheLesserBoundary() {
        assertTrue(ratio(0.5, "LesserOrEqual", fixture.entity));
        hp(fixture.entity, 100, 51);
        assertFalse(ratio(0.5, "LesserOrEqual", fixture.entity));
    }

    @Test
    void namedHealthThresholdHonorsGreaterOrEqual() {
        assertTrue(ratio("threshold", "GreaterOrEqual", fixture.entity));
        hp(fixture.entity, 100, 49);
        assertFalse(ratio("threshold", "GreaterOrEqual", fixture.entity));
    }

    @Test
    void strictLesserDoesNotIncludeTheBoundary() {
        assertFalse(ratio("threshold", "Lesser", fixture.entity));
        hp(fixture.entity, 100, 49);
        assertTrue(ratio("threshold", "Lesser", fixture.entity));
    }

    @Test
    void equalityIsNotInterpretedAsGreaterThan() {
        assertTrue(ratio("threshold", "Equal", fixture.entity));
        hp(fixture.entity, 100, 51);
        assertFalse(ratio("threshold", "Equal", fixture.entity));
    }

    @Test
    void defaultHealthComparisonRemainsStrictlyGreater() {
        assertFalse(ratio(0.5, null, fixture.entity));
        hp(fixture.entity, 100, 51);
        assertTrue(ratio(0.5, null, fixture.entity));
    }

    @Test
    void zeroIsAValidHealthThreshold() {
        hp(fixture.entity, 100, 0);
        assertFalse(ratio(0, null, fixture.entity));
        assertTrue(ratio(0, "LesserOrEqual", fixture.entity));
    }

    @Test
    void anEntityWithoutMaximumHealthCannotPassAHealthCondition() {
        hp(fixture.entity, 0, 0);
        assertFalse(ratio("threshold", null, fixture.entity));
        assertFalse(ratio(0.5, "LesserOrEqual", null));
    }

    @Test
    void healthThresholdCanUseAnAbilityExpression() {
        assertTrue(ratio(List.of("threshold", 2, "DIV"), null, fixture.entity));
        hp(fixture.entity, 100, 20);
        assertFalse(ratio(List.of("threshold", 2, "DIV"), null, fixture.entity));
    }

    @Test
    void globalComparisonReadsTheNamedLimit() {
        fixture.entity.getGlobalAbilityValues().put("count", 2f);
        assertTrue(check("""
                {"$type":"ByTargetGlobalValue","key":"count","value":"countLimit","compareType":"LessAndEqual"}
                """, fixture.entity));
        fixture.entity.getGlobalAbilityValues().put("count", 4f);
        assertFalse(check("""
                {"$type":"ByTargetGlobalValue","key":"count","value":"countLimit","compareType":"LessAndEqual"}
                """, fixture.entity));
    }

    @Test
    void globalComparisonEvaluatesTheResourceExpression() {
        fixture.entity.getGlobalAbilityValues().put("count", 2f);
        assertTrue(check("""
                {"$type":"ByTargetGlobalValue","key":"count","value":["countLimit",1,"SUB"],"compareType":"Equal"}
                """, fixture.entity));
    }

    @Test
    void predicateValuesUseTheSameFightAndGlobalPropertiesAsActions() {
        fixture.entity.getGlobalAbilityValues().put("reference", 7f);
        fixture.entity.getGlobalAbilityValues().put("count", 7f);
        assertTrue(check("""
                {"$type":"ByTargetGlobalValue","key":"count","value":"%reference"}
                """, fixture.entity));
        fixture.entity.getGlobalAbilityValues().put("count", 50f);
        assertTrue(check("""
                {"$type":"ByTargetGlobalValue","key":"count","value":"FIGHT_PROP_CUR_HP"}
                """, fixture.entity));
    }

    @Test
    void numericAndMissingGlobalValuesKeepTheirExistingMeaning() {
        fixture.entity.getGlobalAbilityValues().put("count", 2f);
        assertTrue(check("{\"$type\":\"ByTargetGlobalValue\",\"key\":\"count\",\"value\":2}", fixture.entity));
        assertTrue(check("{\"$type\":\"ByTargetGlobalValue\",\"key\":\"absent\"}", fixture.entity));
    }

    @Test
    void teamAttachmentEvaluatesNumericHealthThresholdPerMember() {
        var low = member(40);
        var high = member(80);
        assertDoesNotThrow(() -> new ActionAttachModifier().execute(ability, action("""
                {"$type":"AttachModifier","modifierName":"Buff","target":"AllPlayerAvatars",
                 "predicates":[{"$type":"ByTargetHPRatio","HPRatio":0.5,"logic":"LesserOrEqual"}]}
                """), ByteString.EMPTY, fixture.entity));
        assertTrue(hasBuff(low));
        assertFalse(hasBuff(high));
    }

    @Test
    void teamAttachmentHonorsOtherSupportedConditions() {
        var blocked = member(80);
        var allowed = member(80);
        new ActionApplyModifier().execute(ability,
                action("{\"$type\":\"ApplyModifier\",\"modifierName\":\"Blocked\"}"), ByteString.EMPTY, blocked);
        new ActionAttachModifier().execute(ability, action("""
                {"$type":"AttachModifier","modifierName":"Buff","target":"AllPlayerAvatars",
                 "predicates":[{"$type":"ByHasModifier","modifierName":"Blocked"}]}
                """), ByteString.EMPTY, fixture.entity);
        assertTrue(hasBuff(blocked));
        assertFalse(hasBuff(allowed));
    }

    @Test
    void singleAttachmentAlsoHonorsItsConditions() {
        new ActionAttachModifier().execute(ability, action("""
                {"$type":"AttachModifier","modifierName":"Buff",
                 "predicates":[{"$type":"ByTargetHPRatio","HPRatio":"threshold"}]}
                """), ByteString.EMPTY, fixture.entity);
        assertFalse(hasBuff(fixture.entity));
    }

    @Test
    void bennettsActualHealingConditionIncludesSeventyPercentAndExcludesHigherHealth() throws Exception {
        var config = JsonUtils.loadToList(Path.of(
                "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Bennett.json"), AbilityConfigData.class);
        var data = config.stream().map(row -> row.Default)
                .filter(row -> row.abilityName.equals("Avatar_Bennett_ElementalBurst_Gadget")).findFirst().orElseThrow();
        ability = new Ability(data, fixture.entity, fixture.player);
        assertEquals(0.7f, ability.getAbilitySpecials().getFloat("HPTreshhold"));
        @SuppressWarnings("unchecked")
        var predicate = (Map<String, Object>) data.modifiers.get("WarmField_Handler")
                .onThinkInterval[2].predicates.get(1);
        assertEquals("LesserOrEqual", predicate.get("logic"));
        hp(fixture.entity, 100, 70);
        assertTrue(PredicateEvaluator.evaluate(predicate, ability, fixture.entity, fixture.entity, null));
        hp(fixture.entity, 100, 71);
        assertFalse(PredicateEvaluator.evaluate(predicate, ability, fixture.entity, fixture.entity, null));
    }

    @Test
    void alhaithamsActualAttackCountConditionUsesTheOverriddenLimit() throws Exception {
        var config = JsonUtils.loadToList(Path.of(
                "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Alhatham.json"), AbilityConfigData.class);
        var data = config.stream().map(row -> row.Default)
                .filter(row -> row.abilityName.equals("SkillObj_Alhatham_ElementalBurst_Dummy")).findFirst().orElseThrow();
        ability = new Ability(data, fixture.entity, fixture.player);
        ability.getAbilitySpecials().put("ForlornLotus_ATKCount", 5f);
        var predicate = data.onAbilityStart[1].targetPredicates.get(0);
        fixture.entity.getGlobalAbilityValues().put("_ABILITY_Alhatham_ForlornLotus_ATKCount", 4f);
        assertTrue(PredicateEvaluator.evaluate(predicate, ability, fixture.entity, fixture.entity, null));
        fixture.entity.getGlobalAbilityValues().put("_ABILITY_Alhatham_ForlornLotus_ATKCount", 5f);
        assertFalse(PredicateEvaluator.evaluate(predicate, ability, fixture.entity, fixture.entity, null));
    }

    @Test
    void furinasActualSummonCallbacksSkipLowHealthMembersAndChargeEligibleMembers() throws Exception {
        var config = JsonUtils.loadToList(Path.of(
                "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Furina.json"), AbilityConfigData.class);
        var low = member(50);
        var high = member(100);
        fixture.player.setSceneLoadState(Player.SceneLoadState.LOADED);
        for (var summon : List.of("Octopus", "SeaHorse", "HermitCrab")) {
            var name = "Furina_" + summon + "_PreAttack";
            var data = config.stream().map(row -> row.Default)
                    .filter(row -> row.abilityName.equals(name)).findFirst().orElseThrow();
            ability = new Ability(data, fixture.entity, fixture.player);
            ability.getAbilitySpecials().put("PreAttack_LoseHP_Threshold", 0.5f);
            ability.getAbilitySpecials().put(summon + "_PreAttack_LoseHP", 0.04f);
            hp(low, 100, 50);
            hp(high, 100, 100);
            var attach = data.modifiers.get(name + "_LoseHP_Handler").onAdded[0];
            assertTrue(new ActionAttachModifier().execute(ability, attach, ByteString.EMPTY, fixture.entity));
            assertEquals(50f, low.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP), summon);
            assertEquals(96f, high.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP), summon);
        }
    }
}
