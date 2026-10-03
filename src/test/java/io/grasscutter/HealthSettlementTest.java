package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.ActionHealHP;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AvatarFightPropUpdateNotifyOuterClass.AvatarFightPropUpdateNotify;
import emu.grasscutter.net.proto.ChangeHpDebtsReasonOuterClass.ChangeHpDebtsReason;
import emu.grasscutter.net.proto.ChangeHpReasonOuterClass.ChangeHpReason;
import emu.grasscutter.net.proto.EntityFightPropChangeReasonNotifyOuterClass.EntityFightPropChangeReasonNotify;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.net.proto.CombatInvocationsNotifyOuterClass.CombatInvocationsNotify;
import emu.grasscutter.net.proto.CombatTypeArgumentOuterClass.CombatTypeArgument;
import emu.grasscutter.net.proto.EvtBeingHealedNotifyOuterClass.EvtBeingHealedNotify;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

class HealthSettlementTest {
    private AbilityTestFixture fixture;

    @BeforeEach
    void scene() throws Exception {
        fixture = new AbilityTestFixture();
        health(fixture.entity, 100, 50, 0);
    }

    @AfterEach
    void restore() { fixture.close(); }

    private void health(GameEntity entity, float maximum, float current, float debt) {
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, maximum);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, current);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, debt);
    }

    private EntityAvatar avatar(float current, float debt) {
        var avatar = new Avatar() {
            @Override public Player getPlayer() { return fixture.player; }
            @Override public long getGuid() { return 1000; }
            @Override public void save() {}
        };
        var entity = new EntityAvatar(fixture.scene, avatar);
        health(entity, 100, current, debt);
        fixture.scene.packets.clear();
        fixture.player.packets.clear();
        return entity;
    }

    private List<EntityFightPropChangeReasonNotify> changes(FightProperty property) throws Exception {
        var result = new ArrayList<EntityFightPropChangeReasonNotify>();
        for (var packet : fixture.scene.packets) {
            if (packet.getOpcode() != PacketOpcodes.EntityFightPropChangeReasonNotify) continue;
            var change = EntityFightPropChangeReasonNotify.parseFrom(packet.getData());
            if (change.getPropType() == property.getId()) result.add(change);
        }
        return result;
    }

    private void bennettHeals(EntityAvatar target) throws Exception {
        var config = JsonUtils.loadToList(Path.of(
                "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Bennett.json"), AbilityConfigData.class);
        var data = config.stream().map(row -> row.Default)
                .filter(row -> row.abilityName.equals("Avatar_Bennett_ElementalBurst_Gadget")).findFirst().orElseThrow();
        var ability = new Ability(data, fixture.entity, fixture.player);
        ability.getAbilitySpecials().put("HealConst", 50f);
        ability.getAbilitySpecials().put("HealMaxHpRatio", 0f);
        new ActionHealHP().execute(ability, data.modifiers.get("WarmField_Handler").onThinkInterval[2],
                ByteString.EMPTY, target);
    }

    private List<EvtBeingHealedNotify> healedEvents() throws Exception {
        var result = new ArrayList<EvtBeingHealedNotify>();
        for (var packet : fixture.scene.packets) {
            if (packet.getOpcode() != PacketOpcodes.CombatInvocationsNotify) continue;
            for (var entry : CombatInvocationsNotify.parseFrom(packet.getData()).getInvokeListList()) {
                if (entry.getArgumentType() == CombatTypeArgument.CombatTypeArgument_COMBAT_BEING_HEALED_NTF) {
                    result.add(EvtBeingHealedNotify.parseFrom(entry.getCombatData()));
                }
            }
        }
        return result;
    }

    @Test
    void partialDebtRepaymentReportsADecreaseAndDoesNotRestoreHealth() throws Exception {
        health(fixture.entity, 100, 50, 50);
        assertEquals(0f, fixture.entity.heal(20));
        assertEquals(50f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(30f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        var changes = changes(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        assertEquals(1, changes.size());
        assertEquals(-20f, changes.get(0).getPropDelta());
        assertEquals(ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_PAY, changes.get(0).getChangeHpDebtsReason());
    }

    @Test
    void finishingDebtRepaymentUsesOnlyTheRemainingHealingForHealth() throws Exception {
        health(fixture.entity, 100, 50, 20);
        assertEquals(30f, fixture.entity.heal(50));
        assertEquals(80f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(0f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        var change = changes(FightProperty.FIGHT_PROP_CUR_HP_DEBTS).get(0);
        assertEquals(-20f, change.getPropDelta());
        assertEquals(ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_PAY_FINISH, change.getChangeHpDebtsReason());
    }

    @Test
    void fullHealthStillAllowsDebtRepaymentWithoutAHealthChange() throws Exception {
        health(fixture.entity, 100, 100, 50);
        assertEquals(0f, fixture.entity.heal(20));
        assertEquals(100f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(30f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        assertTrue(changes(FightProperty.FIGHT_PROP_CUR_HP).isEmpty());
    }

    @Test
    void healingAboveTheCurrentHealthCapDoesNotRemoveHealthWhenDebtIsRepaid() {
        health(fixture.entity, 100, 120, 50);
        assertEquals(0f, fixture.entity.heal(70));
        assertEquals(120f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(0f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
    }

    @Test
    void aNegativeHealingAmountCannotCreateDebt() {
        health(fixture.entity, 100, 50, 30);
        assertEquals(0f, fixture.entity.heal(-20));
        assertEquals(50f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(30f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void nonFiniteHealingCannotOverwriteHealthOrDebt() {
        for (float amount : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            health(fixture.entity, 100, 50, 30);
            assertEquals(0f, fixture.entity.heal(amount));
            assertEquals(50f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
            assertEquals(30f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
            assertTrue(fixture.scene.packets.isEmpty());
        }
    }

    @Test
    void twoPaymentsRepayOnlyTheOutstandingDebt() throws Exception {
        health(fixture.entity, 100, 50, 50);
        assertEquals(0f, fixture.entity.heal(30));
        assertEquals(10f, fixture.entity.heal(30));
        assertEquals(60f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(0f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        var changes = changes(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        assertEquals(List.of(-30f, -20f), changes.stream().map(EntityFightPropChangeReasonNotify::getPropDelta).toList());
    }

    @Test
    void concurrentHealsDoNotLoseHealthOrRepayTheSameDebtTwice() throws Exception {
        health(fixture.entity, 10000, 100, 200);
        var barrier = new CyclicBarrier(8);
        var workers = Executors.newFixedThreadPool(8);
        try {
            var tasks = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 8; i++) {
                tasks.add(workers.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    for (int n = 0; n < 100; n++) fixture.entity.heal(5);
                    return null;
                }));
            }
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
        }
        assertEquals(3900f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(0f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        assertEquals(-200d, changes(FightProperty.FIGHT_PROP_CUR_HP_DEBTS).stream().mapToDouble(EntityFightPropChangeReasonNotify::getPropDelta).sum());
    }

    @Test
    void avatarHealingReportsHealthAddition() throws Exception {
        var entity = avatar(50, 0);
        assertEquals(20f, entity.heal(20));
        var change = changes(FightProperty.FIGHT_PROP_CUR_HP).get(0);
        assertEquals(20f, change.getPropDelta());
        assertEquals(ChangeHpReason.ChangeHpReason_CHANGE_HP_ADD_ABILITY, change.getChangeHpReason());
        assertEquals(PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY, change.getReason());
    }

    @Test
    void mutedHealingStillReportsAnAdditionWithNoAbilityEffect() throws Exception {
        var entity = avatar(50, 0);
        assertEquals(20f, entity.heal(20, true));
        var change = changes(FightProperty.FIGHT_PROP_CUR_HP).get(0);
        assertEquals(ChangeHpReason.ChangeHpReason_CHANGE_HP_ADD_ABILITY, change.getChangeHpReason());
        assertEquals(PropChangeReason.PropChangeReason_PROP_CHANGE_NONE, change.getReason());
    }

    @Test
    void theAvatarOwnerReceivesTheActualCappedHealth() throws Exception {
        var entity = avatar(80, 0);
        assertEquals(20f, entity.heal(100));
        var packet = fixture.player.packets.stream()
                .filter(p -> p.getOpcode() == PacketOpcodes.AvatarFightPropUpdateNotify).findFirst().orElseThrow();
        var update = AvatarFightPropUpdateNotify.parseFrom(packet.getData());
        assertEquals(entity.getAvatar().getGuid(), update.getAvatarGuid());
        assertEquals(100f, update.getFightPropMapOrThrow(FightProperty.FIGHT_PROP_CUR_HP.getId()));
        assertEquals(20f, changes(FightProperty.FIGHT_PROP_CUR_HP).get(0).getPropDelta());
    }

    @Test
    void payingDebtAloneDoesNotReportRestoredHealth() throws Exception {
        var entity = avatar(50, 50);
        assertEquals(0f, entity.heal(20));
        assertTrue(changes(FightProperty.FIGHT_PROP_CUR_HP).isEmpty());
        assertTrue(fixture.player.packets.isEmpty());
        assertEquals(30f, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
    }

    @Test
    void anOrdinaryHealDoesNotRepairADeadFlagEvenIfHealthWasWrittenSeparately() {
        var entity = avatar(0, 0);
        entity.checkIfDead();
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 50);
        fixture.scene.packets.clear();
        assertEquals(0f, entity.heal(20));
        assertFalse(entity.isAlive());
        assertEquals(50f, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void bennettsActualCallbackReportsHealingOnlyAfterTheDebtHasBeenPaid() throws Exception {
        var entity = avatar(50, 20);
        bennettHeals(entity);
        assertEquals(80f, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(0f, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        assertEquals(-20f, changes(FightProperty.FIGHT_PROP_CUR_HP_DEBTS).get(0).getPropDelta());
        assertEquals(ChangeHpReason.ChangeHpReason_CHANGE_HP_ADD_ABILITY,
                changes(FightProperty.FIGHT_PROP_CUR_HP).get(0).getChangeHpReason());
        var events = healedEvents();
        assertEquals(1, events.size());
        assertEquals(entity.getId(), events.get(0).getTargetId());
        assertEquals(50f, events.get(0).getHealAmount());
        assertEquals(30f, events.get(0).getRealHealAmount());
    }

    @Test
    void bennettsActualCallbackDoesNotReportRestoredHealthWhenTheDebtConsumesAllHealing() throws Exception {
        var entity = avatar(50, 60);
        bennettHeals(entity);
        assertEquals(50f, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(10f, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        assertEquals(-50f, changes(FightProperty.FIGHT_PROP_CUR_HP_DEBTS).get(0).getPropDelta());
        assertTrue(changes(FightProperty.FIGHT_PROP_CUR_HP).isEmpty());
        assertTrue(healedEvents().isEmpty());
    }
}
