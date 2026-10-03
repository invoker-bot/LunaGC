package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.ActionReviveAvatar;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarLifeStateChangeNotifyOuterClass.AvatarLifeStateChangeNotify;
import emu.grasscutter.net.proto.AvatarFightPropUpdateNotifyOuterClass.AvatarFightPropUpdateNotify;
import emu.grasscutter.net.proto.EntityFightPropUpdateNotifyOuterClass.EntityFightPropUpdateNotify;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;

class AbilityRevivalTest {
    private AbilityTestFixture fixture;
    private Ability ability;

    @BeforeEach
    void scene() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setWorld(fixture.scene.getWorld());
        ability = new Ability(JsonUtils.decode("{\"abilityName\":\"Test_Revival\"}", AbilityData.class),
                fixture.entity, fixture.player);
    }

    @AfterEach
    void restore() { fixture.close(); }

    private EntityAvatar member(float maxHp, float currentHp) {
        long guid = fixture.player.getTeamManager().getActiveTeam().size() + 1000;
        var avatar = new Avatar() {
            @Override public Player getPlayer() { return fixture.player; }
            @Override public long getGuid() { return guid; }
            @Override public void save() {}
        };
        var entity = new EntityAvatar(fixture.scene, avatar);
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, maxHp);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, currentHp);
        entity.checkIfDead();
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        fixture.scene.getEntities().put(entity.getId(), entity);
        fixture.scene.packets.clear();
        return entity;
    }

    private boolean revive(String json) {
        return new ActionReviveAvatar().execute(ability, JsonUtils.decode(json, AbilityModifierAction.class),
                ByteString.EMPTY, fixture.entity);
    }

    private AbilityData actual(String character, String name) throws Exception {
        return JsonUtils.loadToList(Path.of("resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_"
                + character + ".json"), AbilityConfigData.class).stream().map(row -> row.Default)
                .filter(row -> row.abilityName.equals(name)).findFirst().orElseThrow();
    }

    @Test
    void teamRevivalRestoresDeadMembersAndLeavesLivingMembersUnchanged() {
        var first = member(100, 0);
        var second = member(200, 0);
        var living = member(100, 77);
        assertTrue(revive("{\"$type\":\"ReviveAvatar\",\"target\":\"AllPlayerAvatars\",\"amountByTargetMaxHPRatio\":0.5}"));
        assertTrue(first.isAlive());
        assertTrue(second.isAlive());
        assertEquals(50f, first.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(100f, second.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(77f, living.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void localAvatarRevivalDoesNotReviveOtherDeadMembers() {
        var current = member(100, 0);
        var other = member(100, 0);
        assertTrue(revive("{\"$type\":\"ReviveAvatar\",\"target\":\"CurLocalAvatar\",\"amountByTargetMaxHPRatio\":1}"));
        assertTrue(current.isAlive());
        assertFalse(other.isAlive());
        assertEquals(0f, other.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void anExplicitTargetRevivesOnlyThatAvatar() {
        var current = member(100, 0);
        var target = member(100, 0);
        var action = JsonUtils.decode(
                "{\"$type\":\"ReviveAvatar\",\"target\":\"Target\",\"amountByTargetMaxHPRatio\":1}",
                AbilityModifierAction.class);
        new ActionReviveAvatar().execute(ability, action, ByteString.EMPTY, target);
        assertFalse(current.isAlive());
        assertTrue(target.isAlive());
        assertEquals(100f, target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void aRevivalActionHonorsItsConditions() {
        var dead = member(100, 0);
        revive("""
                {"$type":"ReviveAvatar","target":"AllPlayerAvatars","amountByTargetMaxHPRatio":1,
                "predicates":[{"$type":"ByTargetHPRatio","HPRatio":0.5,"logic":"Greater"}]}
                """);
        assertFalse(dead.isAlive());
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void revivedMemberCanTakeDamageAfterItsPreviousDeathWasProcessed() {
        var dead = member(100, 0);
        assertTrue(dead.tryBeginDeath());
        revive("{\"$type\":\"ReviveAvatar\",\"target\":\"CurLocalAvatar\",\"amountByTargetMaxHPRatio\":0.5}");
        dead.damage(10);
        assertEquals(40f, dead.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertTrue(dead.isAlive());
    }

    @Test
    void revivalNotifiesTheClientOfTheAliveStateAfterHealthIsRestored() throws Exception {
        var dead = member(100, 0);
        revive("{\"$type\":\"ReviveAvatar\",\"target\":\"AllPlayerAvatars\",\"amountByTargetMaxHPRatio\":1}");
        var states = fixture.scene.packets.stream().filter(p -> p.getOpcode() == PacketOpcodes.AvatarLifeStateChangeNotify).toList();
        assertEquals(1, states.size());
        assertEquals(LifeState.LIFE_ALIVE.getValue(), AvatarLifeStateChangeNotify.parseFrom(states.get(0).getData()).getLifeState());
        var avatarHealth = fixture.player.packets.stream()
                .filter(p -> p.getOpcode() == PacketOpcodes.AvatarFightPropUpdateNotify).findFirst().orElseThrow();
        var avatarUpdate = AvatarFightPropUpdateNotify.parseFrom(avatarHealth.getData());
        assertEquals(dead.getAvatar().getGuid(), avatarUpdate.getAvatarGuid());
        assertEquals(100f, avatarUpdate.getFightPropMapOrThrow(FightProperty.FIGHT_PROP_CUR_HP.getId()));
        var entityHealth = fixture.scene.packets.stream()
                .filter(p -> p.getOpcode() == PacketOpcodes.EntityFightPropUpdateNotify).findFirst().orElseThrow();
        var entityUpdate = EntityFightPropUpdateNotify.parseFrom(entityHealth.getData());
        assertEquals(dead.getId(), entityUpdate.getEntityId());
        assertEquals(100f, entityUpdate.getFightPropMapOrThrow(FightProperty.FIGHT_PROP_CUR_HP.getId()));
        assertTrue(fixture.scene.packets.indexOf(entityHealth) < fixture.scene.packets.indexOf(states.get(0)));
    }

    @Test
    void ordinaryHealingStillDoesNotReviveADeadAvatar() {
        var dead = member(100, 0);
        assertEquals(0f, dead.heal(50));
        assertFalse(dead.isAlive());
        assertEquals(0f, dead.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void invalidRevivalAmountsDoNotEmitDeathOrAliveNotifications() {
        var dead = member(100, 0);
        for (float ratio : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            var action = JsonUtils.decode("{\"$type\":\"ReviveAvatar\",\"target\":\"CurLocalAvatar\"}", AbilityModifierAction.class);
            action.amountByTargetMaxHPRatio = new DynamicFloat(ratio);
            new ActionReviveAvatar().execute(ability, action, ByteString.EMPTY, fixture.entity);
        }
        assertFalse(dead.isAlive());
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void revivedHealthCannotExceedMaximumHealth() {
        var dead = member(100, 0);
        revive("{\"$type\":\"ReviveAvatar\",\"target\":\"CurLocalAvatar\",\"amountByTargetMaxHPRatio\":2}");
        assertEquals(100f, dead.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void foodRevivalClearsDeathBeforeTheFollowingHeal() {
        var dead = member(100, 0);
        assertTrue(fixture.player.getTeamManager().reviveAvatar(dead.getAvatar()));
        assertTrue(dead.isAlive());
        assertTrue(fixture.player.getTeamManager().healAvatar(dead.getAvatar(), 30, 0));
        assertEquals(31f, dead.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void partyRespawnRestoresFortyPercentHealthAndClearsTheDeathGuard() {
        var first = member(100, 0);
        var second = member(200, 0);
        assertTrue(first.tryBeginDeath());
        assertTrue(second.tryBeginDeath());
        fixture.player.getTeamManager().respawnTeam();
        assertTrue(first.isAlive());
        assertTrue(second.isAlive());
        assertEquals(40f, first.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(80f, second.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        first.damage(10);
        assertEquals(30f, first.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertTrue(fixture.player.packets.stream().anyMatch(p -> p.getOpcode() == PacketOpcodes.WorldPlayerReviveRsp));
    }

    @Test
    void barbaraActualResourceRevivesThePartyToFullHealth() throws Exception {
        var dead = member(100, 0);
        var data = actual("Barbara", "Avatar_Barbara_ReBorn");
        ability = new Ability(data, fixture.entity, fixture.player);
        new ActionReviveAvatar().execute(ability, data.onAbilityStart[2], ByteString.EMPTY, fixture.entity);
        assertTrue(dead.isAlive());
        assertEquals(100f, dead.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void qiqiActualResourceReadsTheObfuscatedRatioAndRevivesHalfHealth() throws Exception {
        var dead = member(100, 0);
        var data = actual("Qiqi", "Avatar_Qiqi_Revive");
        ability = new Ability(data, fixture.entity, fixture.player);
        assertEquals(0.5f, data.onAbilityStart[2].amountByTargetMaxHPRatio.get(ability));
        new ActionReviveAvatar().execute(ability, data.onAbilityStart[2], ByteString.EMPTY, fixture.entity);
        assertTrue(dead.isAlive());
        assertEquals(50f, dead.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void columbinaActualResourceUsesTheCastersFriendshipGlobal() throws Exception {
        var current = member(200, 0);
        var other = member(100, 0);
        var data = actual("Columbina", "Avatar_Columbina_Revive");
        ability = new Ability(data, fixture.entity, fixture.player);
        fixture.entity.getGlobalAbilityValues().put("_ABILITY_Columbina_Friendship", 5f);
        var action = data.modifiers.get("Avatar_Columbina_Revive_Handler").onKill[0].successActions[0];
        new ActionReviveAvatar().execute(ability, action, ByteString.EMPTY, fixture.entity);
        assertTrue(current.isAlive());
        assertEquals(100f, current.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertFalse(other.isAlive());
    }
}
