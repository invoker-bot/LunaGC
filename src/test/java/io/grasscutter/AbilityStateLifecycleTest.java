package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.*;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.utils.JsonUtils;
import emu.grasscutter.utils.Utils;
import java.util.Map;
import org.junit.jupiter.api.*;

class AbilityStateLifecycleTest {
    private AbilityTestFixture fixture;
    private AbilityData data;
    private AbilityData previousData;
    private Ability ability;

    @BeforeEach
    void scene() throws Exception {
        fixture = new AbilityTestFixture();
        data = JsonUtils.decode("""
                {"abilityName":"Test_StateLifecycle","abilitySpecials":{"coefficient":3},
                 "modifiers":{"Focus":{},"Other":{}}}
                """, AbilityData.class);
        previousData = GameData.getAbilityDataMap().put(data.abilityName, data);
        ability = new Ability(data, fixture.entity, fixture.player);
        fixture.entity.getInstancedAbilities().add(ability);
    }

    @AfterEach
    void restore() {
        if (previousData == null) GameData.getAbilityDataMap().remove(data.abilityName);
        else GameData.getAbilityDataMap().put(data.abilityName, previousData);
        fixture.close();
    }

    private AbilityTestFixture.TestEntity entity(int id) {
        var target = new AbilityTestFixture.TestEntity(fixture.scene);
        target.setId(id);
        fixture.scene.getEntities().put(id, target);
        return target;
    }

    private void add(int target, int source, int abilityId, int modifierId, int localId,
                     AbilityString parent) throws Exception {
        var change = AbilityMetaModifierChange.newBuilder()
                .setAction(ModifierAction.MODIFIER_ACTION_ADDED)
                .setParentAbilityName(parent).setModifierLocalId(localId).build();
        fixture.player.getAbilityManager().onAbilityInvoke(AbilityInvokeEntry.newBuilder()
                .setEntityId(target)
                .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                .setHead(AbilityInvokeEntryHead.newBuilder().setTargetId(source)
                        .setInstancedAbilityId(abilityId).setInstancedModifierId(modifierId))
                .setAbilityData(change.toByteString()).build());
    }

    private AbilityString parent() { return AbilityString.newBuilder().setStr(data.abilityName).build(); }

    private void remove(int entity, int modifierId) throws Exception {
        fixture.player.getAbilityManager().onAbilityInvoke(AbilityInvokeEntry.newBuilder()
                .setEntityId(entity)
                .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                .setHead(AbilityInvokeEntryHead.newBuilder().setInstancedModifierId(modifierId))
                .setAbilityData(AbilityMetaModifierChange.newBuilder()
                        .setAction(ModifierAction.MODIFIER_ACTION_REMOVED).build().toByteString()).build());
    }

    private boolean has(AbilityTestFixture.TestEntity target) {
        return PredicateEvaluator.evaluate(Map.of("$type", "ByHasModifier", "modifierName", "Focus"),
                ability, fixture.entity, target, null);
    }

    private AbilityModifierAction named(String type) {
        return JsonUtils.decode("{\"$type\":\"" + type + "\",\"modifierName\":\"Focus\"}",
                AbilityModifierAction.class);
    }

    @Test
    void namedParentReusesTheSkillInstanceAndItsOverrideValues() throws Exception {
        ability.getAbilitySpecials().put("coefficient", 321f);
        add(42, 0, 1, 200, 0, parent());
        var actual = fixture.entity.getInstancedModifiers().get(200).getAbility();
        assertSame(ability, actual);
        assertEquals(321f, actual.getAbilitySpecials().getFloat("coefficient"));
    }

    @Test
    void hashedParentAlsoPreservesTheExistingInstance() throws Exception {
        int hash = Utils.abilityHash(data.abilityName);
        var old = GameData.getAbilityHashes().put(hash, data.abilityName);
        try {
            add(42, 0, 1, 200, 0, AbilityString.newBuilder().setHash(hash).build());
            assertSame(ability, fixture.entity.getInstancedModifiers().get(200).getAbility());
        } finally {
            if (old == null) GameData.getAbilityHashes().remove(hash);
            else GameData.getAbilityHashes().put(hash, old);
        }
    }

    @Test
    void attachedStateKeepsTheCastersSkillInsteadOfCreatingAMonstersCopy() throws Exception {
        var monster = entity(43);
        add(43, 42, 1, 200, 0, parent());
        assertSame(ability, monster.getInstancedModifiers().get(200).getAbility());
        assertSame(fixture.entity, monster.getInstancedModifiers().get(200).getAbility().getOwner());
    }

    @Test
    void explicitParentCanAttachWithoutAnInstancedAbilityId() throws Exception {
        var monster = entity(43);
        add(43, 42, 0, 200, 0, parent());
        assertNotNull(monster.getInstancedModifiers().get(200));
    }

    @Test
    void namedParentDoesNotBindToADifferentSkillAtTheSameIndex() throws Exception {
        var unrelated = new Ability(JsonUtils.decode("{\"abilityName\":\"Test_Unrelated\"}", AbilityData.class),
                fixture.entity, fixture.player);
        fixture.entity.getInstancedAbilities().add(0, unrelated);
        add(42, 0, 1, 200, 0, parent());
        assertSame(ability, fixture.entity.getInstancedModifiers().get(200).getAbility());
    }

    @Test
    void negativeModifierIndexDoesNotBreakTheInvocationOrExistingState() throws Exception {
        add(42, 0, 1, 200, 0, parent());
        assertDoesNotThrow(() -> add(42, 0, 1, 201, -1, parent()));
        assertTrue(fixture.entity.getInstancedModifiers().containsKey(200));
        assertFalse(fixture.entity.getInstancedModifiers().containsKey(201));
    }

    @Test
    void conditionRecognizesAClientAttachedDebuffUntilItIsRemoved() throws Exception {
        var monster = entity(43);
        add(43, 42, 1, 200, 0, parent());
        assertTrue(has(monster));
        assertFalse(has(fixture.entity));
        remove(43, 200);
        assertFalse(has(monster));
    }

    @Test
    void applyingAndRemovingAStateOnlyAffectsItsTarget() {
        var first = entity(43);
        var second = entity(44);
        var apply = new ActionApplyModifier();
        apply.execute(ability, named("ApplyModifier"), ByteString.EMPTY, first);
        apply.execute(ability, named("ApplyModifier"), ByteString.EMPTY, second);
        assertTrue(has(first));
        assertTrue(has(second));
        assertFalse(has(fixture.entity));
        new ActionRemoveModifier().execute(ability, named("RemoveModifier"), ByteString.EMPTY, first);
        assertFalse(has(first));
        assertTrue(has(second));
    }

    @Test
    void uniqueStateRemovalDoesNotStripAnotherTarget() {
        var first = entity(43);
        var second = entity(44);
        var attach = new ActionAttachModifier();
        attach.execute(ability, named("AttachModifier"), ByteString.EMPTY, first);
        attach.execute(ability, named("AttachModifier"), ByteString.EMPTY, second);
        new ActionRemoveUniqueModifier().execute(ability, named("RemoveUniqueModifier"), ByteString.EMPTY, first);
        assertFalse(has(first));
        assertTrue(has(second));
    }

    @Test
    void explicitRemovalAlsoClearsTheMatchingClientInstance() throws Exception {
        var monster = entity(43);
        add(43, 42, 1, 200, 0, parent());
        new ActionRemoveModifier().execute(ability, named("RemoveModifier"), ByteString.EMPTY, monster);
        assertFalse(monster.getInstancedModifiers().containsKey(200));
        assertFalse(has(monster));
    }

    @Test
    void removingAnOlderInstanceKeepsTheNewerStateActive() throws Exception {
        add(42, 0, 1, 200, 0, parent());
        add(42, 0, 1, 201, 0, parent());
        remove(42, 200);
        assertTrue(has(fixture.entity));
        remove(42, 201);
        assertFalse(has(fixture.entity));
    }

    @Test
    void replacingAClientIdWithAnotherStateClearsThePreviousName() throws Exception {
        add(42, 0, 1, 200, 0, parent());
        add(42, 0, 1, 200, 1, parent());
        assertFalse(has(fixture.entity));
        assertEquals("Other", fixture.entity.getInstancedModifiers().get(200).getName());
    }

    @Test
    void removingTheNewerInstanceKeepsAnOlderInstanceUntilItAlsoExpires() throws Exception {
        add(42, 0, 1, 200, 0, parent());
        add(42, 0, 1, 201, 0, parent());
        remove(42, 201);
        assertTrue(has(fixture.entity));
        remove(42, 200);
        assertFalse(has(fixture.entity));
    }

    @Test
    void repeatedDynamicStateUsesTheSameAbilityAndKeepsUpdatedParameters() throws Exception {
        fixture.entity.getInstancedAbilities().clear();
        add(42, 0, 0, 200, 0, parent());
        var first = fixture.entity.getInstancedModifiers().get(200).getAbility();
        first.getAbilitySpecials().put("coefficient", 321f);
        add(42, 0, 0, 201, 1, parent());
        var second = fixture.entity.getInstancedModifiers().get(201).getAbility();
        assertSame(first, second);
        assertEquals(321f, second.getAbilitySpecials().getFloat("coefficient"));
    }

    @Test
    void removingOneCastersStateKeepsAnotherCastersStateOnTheSameTarget() throws Exception {
        var target = entity(43);
        var otherCaster = entity(44);
        var otherAbility = new Ability(data, otherCaster, fixture.player);
        otherCaster.getInstancedAbilities().add(otherAbility);
        add(43, 42, 1, 200, 0, parent());
        add(43, 44, 1, 201, 0, parent());
        new ActionRemoveModifier().execute(ability, named("RemoveModifier"), ByteString.EMPTY, target);
        assertFalse(target.getInstancedModifiers().containsKey(200));
        assertSame(otherAbility, target.getInstancedModifiers().get(201).getAbility());
        assertTrue(has(target));
        remove(43, 201);
        assertFalse(has(target));
    }

    @Test
    void attachedStateIsAvailableToLaterActionsInTheSameCallback() throws Exception {
        data.modifiers.get("Focus").onAdded = JsonUtils.decode("""
                [{"$type":"ApplyModifier","modifierName":"Other"},
                 {"$type":"Predicated","targetPredicates":[{"$type":"ByHasModifier","modifierName":"Other"}],
                  "successActions":[{"$type":"SetGlobalValue","key":"chainCompleted","value":1}]}]
                """, AbilityModifierAction[].class);
        add(42, 0, 1, 200, 0, parent());
        assertEquals(1f, fixture.entity.getGlobalAbilityValues().get("chainCompleted"));
    }

    @Test
    void clientRemovalReleasesTheSourceReference() throws Exception {
        var target = entity(43);
        add(43, 42, 1, 200, 0, parent());
        remove(43, 200);
        assertTrue(target.getAppliedAbilityModifiers().isEmpty());
    }

    @Test
    void explicitRemovalReleasesTheSourceReference() throws Exception {
        var target = entity(43);
        add(43, 42, 1, 200, 0, parent());
        new ActionRemoveModifier().execute(ability, named("RemoveModifier"), ByteString.EMPTY, target);
        assertTrue(target.getAppliedAbilityModifiers().isEmpty());
    }

    @Test
    void replacingTheLastStateReleasesThePreviousCastersReference() throws Exception {
        var target = entity(43);
        var otherCaster = entity(44);
        var otherAbility = new Ability(data, otherCaster, fixture.player);
        otherCaster.getInstancedAbilities().add(otherAbility);
        add(43, 42, 1, 200, 0, parent());
        add(43, 44, 1, 200, 0, parent());
        assertFalse(target.getAppliedAbilityModifiers().containsKey(ability));
        assertTrue(target.getAppliedAbilityModifiers().containsKey(otherAbility));
    }

    @Test
    void dynamicAbilitySurvivesExpiryWithoutRetainingExpiredState() throws Exception {
        fixture.entity.getInstancedAbilities().clear();
        add(42, 0, 0, 200, 0, parent());
        var first = fixture.entity.getInstancedModifiers().get(200).getAbility();
        first.getAbilitySpecials().put("coefficient", 321f);
        remove(42, 200);
        assertTrue(fixture.entity.getAppliedAbilityModifiers().isEmpty());
        add(42, 0, 0, 201, 1, parent());
        var second = fixture.entity.getInstancedModifiers().get(201).getAbility();
        assertSame(first, second);
        assertEquals(321f, second.getAbilitySpecials().getFloat("coefficient"));
    }

    @Test
    void manyExpiredCastersDoNotAccumulateOnTheTarget() throws Exception {
        var target = entity(43);
        for (int id = 100; id < 200; id++) {
            var source = entity(id);
            source.getInstancedAbilities().add(new Ability(data, source, fixture.player));
            add(43, id, 1, 200, 0, parent());
            remove(43, 200);
        }
        assertTrue(target.getAppliedAbilityModifiers().isEmpty());
        assertTrue(target.getInstancedModifiers().isEmpty());
    }
}
