package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityTargetSelector;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.utils.JsonUtils;
import java.util.*;
import org.junit.jupiter.api.*;

class AbilityTargetSelectorTest {
    private AbilityTestFixture fixture;
    private Ability ability;
    private Player partner;

    @BeforeEach
    void scene() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setWorld(fixture.scene.getWorld());
        partner = new AbilityTestFixture.TestPlayer();
        partner.setWorld(fixture.scene.getWorld());
        ability = new Ability(JsonUtils.decode("{\"abilityName\":\"Test_TargetSelector\"}", AbilityData.class),
                fixture.entity, fixture.player);
    }

    @AfterEach
    void restore() { fixture.close(); }

    private EntityAvatar avatar(Player owner) {
        var avatar = new Avatar() {
            @Override public Player getPlayer() { return owner; }
        };
        var entity = new EntityAvatar(fixture.scene, avatar) {
            @Override public Position getPosition() { return new Position(); }
        };
        fixture.scene.getEntities().put(entity.getId(), entity);
        return entity;
    }

    private EntityClientGadget gadget(Player owner, int id, float x, float y) {
        var entity = new EntityClientGadget(fixture.scene, owner, EvtCreateGadgetNotify.newBuilder()
                .setEntityId(id).setInitPos(new Position(x, y, 0).toProto()).build());
        fixture.scene.getEntities().put(id, entity);
        return entity;
    }

    private Map<String, Object> selector(String camp, String type) {
        return Map.of("$type", "SelectTargetsByShape", "campTargetType", camp,
                "entityTypes", List.of(type), "shapeName", "CircleR20H10");
    }

    private List<GameEntity> select(String camp, String type) {
        return AbilityTargetSelector.select(selector(camp, type), ability, fixture.entity);
    }

    @Test
    void enemySelectionDoesNotTreatACoopPartnerAsAnEnemy() {
        avatar(fixture.player);
        avatar(partner);
        assertTrue(select("Enemy", "Avatar").isEmpty());
    }

    @Test
    void allianceSelectionIncludesACoopPartnersAvatar() {
        var own = avatar(fixture.player);
        var ally = avatar(partner);
        assertEquals(Set.of(own, ally), new HashSet<>(select("Alliance", "Avatar")));
    }

    @Test
    void enemySelectionSkipsBothPlayersSummons() {
        gadget(fixture.player, 100, 2, 0);
        gadget(partner, 101, 3, 0);
        var enemy = gadget(null, 102, 4, 0);
        assertEquals(List.of(enemy), select("Enemy", "Gadget"));
    }

    @Test
    void allianceSelectionIncludesBothPlayersSummons() {
        var own = gadget(fixture.player, 100, 2, 0);
        var ally = gadget(partner, 101, 3, 0);
        gadget(null, 102, 4, 0);
        assertEquals(Set.of(own, ally), new HashSet<>(select("SelfCamp", "Gadget")));
    }

    @Test
    void anOwnerWithoutTheSameWorldIsNotAutomaticallyAnAlly() {
        var outsider = new AbilityTestFixture.TestPlayer();
        var other = avatar(outsider);
        assertEquals(List.of(other), select("Enemy", "Avatar"));
        assertTrue(select("Alliance", "Avatar").isEmpty());
    }

    @Test
    void missingAbilityPlayerDoesNotTurnUnownedGadgetsIntoAllies() {
        var enemy = gadget(null, 102, 4, 0);
        var ownerless = new Ability(ability.getData(), fixture.entity, null);
        assertEquals(List.of(enemy), AbilityTargetSelector.select(selector("Enemy", "Gadget"),
                ownerless, fixture.entity));
    }

    @Test
    void nearestEnemyStillHonorsDistanceHeightAndLimitAfterSkippingAnAlly() {
        gadget(partner, 100, 1, 0);
        var nearest = gadget(null, 101, 3, 0);
        gadget(null, 102, 10, 0);
        gadget(null, 103, 25, 0);
        gadget(null, 104, 1, 11);
        var config = new HashMap<>(selector("Enemy", "Gadget"));
        config.put("sortType", "Nearest");
        config.put("topLimit", 1);
        assertEquals(List.of(nearest), AbilityTargetSelector.select(config, ability, fixture.entity));
    }

    @Test
    void unsupportedSelectorKeepsTheExistingFallbackContract() {
        assertNull(AbilityTargetSelector.select(Map.of("$type", "UnknownSelector"), ability, fixture.entity));
    }
}
