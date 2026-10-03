package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.*;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.utils.JsonUtils;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class AbilityActionBehaviorTest {
    private AbilityTestFixture fixture;
    private Ability ability;

    @BeforeEach
    void scene() throws Exception {
        fixture = new AbilityTestFixture();
        ability = ability("{\"abilityName\":\"Test_Behavior\"}");
    }

    @AfterEach
    void restore() { fixture.close(); }

    private Ability ability(String json) {
        return new Ability(JsonUtils.decode(json, AbilityData.class), fixture.entity, null);
    }

    private AbilityModifierAction action(String json) {
        return Objects.requireNonNull(JsonUtils.decode(json, AbilityModifierAction.class));
    }

    @Test
    void unboundedCounterDoesNotClampToTheAbsentZeroBounds() {
        fixture.entity.getGlobalAbilityValues().put("count", 4f);
        var add = action("{\"$type\":\"AddGlobalValue\",\"key\":\"count\",\"value\":3}");
        new ActionAddGlobalValue().execute(ability, add, ByteString.EMPTY, fixture.entity);
        assertEquals(7f, fixture.entity.getGlobalAbilityValues().get("count"));
    }

    @Test
    void counterHonorsConfiguredUpperAndLowerBounds() {
        var add = action("{\"$type\":\"AddGlobalValue\",\"key\":\"count\",\"value\":3,"
                + "\"useLimitRange\":true,\"minValue\":2,\"maxValue\":8}");
        fixture.entity.getGlobalAbilityValues().put("count", 7f);
        var handler = new ActionAddGlobalValue();
        handler.execute(ability, add, ByteString.EMPTY, fixture.entity);
        assertEquals(8f, fixture.entity.getGlobalAbilityValues().get("count"));
        add = action("{\"$type\":\"AddGlobalValue\",\"key\":\"count\",\"value\":-20,"
                + "\"useLimitRange\":true,\"minValue\":2,\"maxValue\":8}");
        handler.execute(ability, add, ByteString.EMPTY, fixture.entity);
        assertEquals(2f, fixture.entity.getGlobalAbilityValues().get("count"));
    }

    @Test
    void teamCounterWithoutASceneStillUpdatesWithoutThrowing() {
        var team = new AbilityTestFixture.TestEntity(null);
        var add = action("{\"$type\":\"AddGlobalValue\",\"key\":\"count\",\"value\":1}");
        assertDoesNotThrow(() -> new ActionAddGlobalValue().execute(ability, add, ByteString.EMPTY, team));
        assertEquals(1f, team.getGlobalAbilityValues().get("count"));
    }

    @Test
    void concurrentHitsDoNotLoseCounterIncrements() throws Exception {
        var add = action("{\"$type\":\"AddGlobalValue\",\"key\":\"count\",\"value\":1,"
                + "\"useLimitRange\":true,\"maxValue\":100000}");
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(16);
        try {
            var futures = new ArrayList<Future<?>>();
            for (int thread = 0; thread < 16; thread++) futures.add(pool.submit(() -> {
                start.await();
                var handler = new ActionAddGlobalValue();
                for (int hit = 0; hit < 100; hit++) handler.execute(ability, add, ByteString.EMPTY, fixture.entity);
                return null;
            }));
            start.countDown();
            for (var task : futures) task.get(10, TimeUnit.SECONDS);
            assertEquals(1600f, fixture.entity.getGlobalAbilityValues().get("count"));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void setValueUsesConfiguredRangeForBothVersions() {
        for (String type : List.of("SetGlobalValue", "SetGlobalValueV2")) {
            var set = action("{\"$type\":\"" + type + "\",\"key\":\"count\",\"value\":20,"
                    + "\"useLimitRange\":true,\"minValue\":-2,\"maxValue\":5}");
            new ActionSetGlobalValue().execute(ability, set, ByteString.EMPTY, fixture.entity);
            assertEquals(5f, fixture.entity.getGlobalAbilityValues().get("count"), type);
            set = action("{\"$type\":\"" + type + "\",\"key\":\"count\",\"value\":-20,"
                    + "\"useLimitRange\":true,\"minValue\":-2,\"maxValue\":5}");
            new ActionSetGlobalValue().execute(ability, set, ByteString.EMPTY, fixture.entity);
            assertEquals(-2f, fixture.entity.getGlobalAbilityValues().get("count"), type);
        }
    }

    @Test
    void setValueWithoutRangePreservesTheWrittenValue() {
        var set = action("{\"$type\":\"SetGlobalValue\",\"key\":\"count\",\"value\":20}");
        new ActionSetGlobalValue().execute(ability, set, ByteString.EMPTY, fixture.entity);
        assertEquals(20f, fixture.entity.getGlobalAbilityValues().get("count"));
    }

    @Test
    void dynamicRangeReadsTheAbilityParameters() {
        ability.getAbilitySpecials().put("maximum", 5f);
        ability.getAbilitySpecials().put("energy", 12f);
        var set = action("{\"$type\":\"SetGlobalValueV2\",\"key\":\"count\",\"value\":\"energy\","
                + "\"useLimitRange\":true,\"maxValue\":\"maximum\"}");
        new ActionSetGlobalValue().execute(ability, set, ByteString.EMPTY, fixture.entity);
        assertEquals(5f, fixture.entity.getGlobalAbilityValues().get("count"));
    }

    @Test
    void nahidaLessAndEqualAllowsTheFirstTargetAndIncludesTheBoundary() {
        var predicate = Map.<String, Object>of("$type", "ByTargetGlobalValue",
                "key", "_ABILITY_Nahida_ElementalArt_TagNum", "value", 7, "compareType", "LessAndEqual");
        for (int count = 0; count <= 8; count++) {
            fixture.entity.getGlobalAbilityValues().put("_ABILITY_Nahida_ElementalArt_TagNum", (float) count);
            assertEquals(count <= 7, PredicateEvaluator.evaluate(predicate, ability,
                    fixture.entity, fixture.entity, null), "Tag count " + count);
        }
    }

    @Test
    void applyingAHitListenerDoesNotPretendAnAttackHasAlreadyLanded() throws Exception {
        ability = ability("""
                {"abilityName":"Test_HitListener","modifiers":{"Listener":{
                    "onAdded":[{"$type":"SetGlobalValue","key":"active","value":1}],
                    "onAttackLanded":[{"$type":"AddGlobalValue","key":"hits","value":1,
                                       "useLimitRange":true,"maxValue":100}]}}}
                """);
        fixture.entity.getInstancedAbilities().add(ability);
        var apply = action("{\"$type\":\"ApplyModifier\",\"modifierName\":\"Listener\"}");
        new ActionApplyModifier().execute(ability, apply, ByteString.EMPTY, fixture.entity);
        drainActions();
        assertEquals(1f, fixture.entity.getGlobalAbilityValues().get("active"));
        assertEquals(0f, fixture.entity.getGlobalAbilityValues().getOrDefault("hits", 0f));
        fixture.player.getAbilityManager().handleServerInvoke(AbilityInvokeEntry.newBuilder()
                .setEntityId(42).setHead(AbilityInvokeEntryHead.newBuilder()
                        .setInstancedAbilityId(1).setLocalId(3 + (3 << 9) + (1 << 15))).build());
        drainActions();
        assertEquals(1f, fixture.entity.getGlobalAbilityValues().get("hits"));
    }

    // All four workers reach the barrier only after earlier action tasks have completed.
    private void drainActions() throws Exception {
        var barrier = new CyclicBarrier(4);
        var futures = new ArrayList<Future<?>>();
        for (int i = 0; i < 4; i++) futures.add(AbilityManager.eventExecutor.submit(() -> {
            barrier.await(5, TimeUnit.SECONDS);
            return null;
        }));
        for (var task : futures) task.get(6, TimeUnit.SECONDS);
    }
}
