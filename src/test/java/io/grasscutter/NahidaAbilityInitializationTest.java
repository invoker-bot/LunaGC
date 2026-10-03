package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.net.proto.AbilityAppliedAbilityOuterClass.AbilityAppliedAbility;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaAddAbilityOuterClass.AbilityMetaAddAbility;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.utils.JsonUtils;
import emu.grasscutter.utils.Utils;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;

/** Client ability IDs must keep identifying the same skill throughout initialization. */
class NahidaAbilityInitializationTest {
    private AbilityTestFixture fixture;
    private final Map<String, AbilityData> originalData = new HashMap<>();
    private AbilityData rayCast;
    private AbilityData click;

    @BeforeEach
    void scene() throws Exception {
        fixture = new AbilityTestFixture();
        var configs =
                JsonUtils.loadToList(
                        Path.of(
                                "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Nahida.json"),
                        AbilityConfigData.class);
        for (var config : configs) {
            var data = config.Default;
            if (data.abilityName.equals("Avatar_Nahida_ElementalArt_RayCast")) rayCast = data;
            if (data.abilityName.equals("Avatar_Nahida_ElementalArt_Click")) click = data;
        }
        originalData.put(
                rayCast.abilityName, GameData.getAbilityDataMap().put(rayCast.abilityName, rayCast));
        originalData.put(click.abilityName, GameData.getAbilityDataMap().put(click.abilityName, click));
    }

    @AfterEach
    void restore() {
        originalData.forEach(
                (name, data) -> {
                    if (data == null) GameData.getAbilityDataMap().remove(name);
                    else GameData.getAbilityDataMap().put(name, data);
                });
        fixture.close();
    }

    private void add(int id, AbilityData data, AbilityScalarValueEntry... overrides)
            throws Exception {
        var applied =
                AbilityAppliedAbility.newBuilder()
                        .setAbilityName(AbilityString.newBuilder().setStr(data.abilityName))
                        .setInstancedAbilityId(id);
        for (var override : overrides) applied.addOverrideMap(override);
        fixture
                .player
                .getAbilityManager()
                .onAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(42)
                                .setHead(AbilityInvokeEntryHead.newBuilder().setInstancedAbilityId(id))
                                .setArgumentType(
                                        AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_ADD_NEW_ABILITY)
                                .setAbilityData(
                                        AbilityMetaAddAbility.newBuilder().setAbility(applied).build().toByteString())
                                .build());
    }

    @Test
    void repeatedInitializationKeepsTheExistingSkillAndItsParameters() throws Exception {
        var existing = new Ability(rayCast, fixture.entity, fixture.player);
        existing.getAbilitySpecials().put("Hold_Damage", 2.5f);
        fixture.entity.getInstancedAbilities().add(existing);
        add(1, rayCast);
        add(1, rayCast);
        assertEquals(1, fixture.entity.getInstancedAbilities().size());
        assertSame(existing, fixture.entity.getInstancedAbilities().get(0));
        assertEquals(2.5f, existing.getAbilitySpecials().getFloat("Hold_Damage"));
    }

    @Test
    void reassignedClientIdReplacesThatSlotInsteadOfAppendingTheSkill() throws Exception {
        fixture.entity.getInstancedAbilities().add(new Ability(click, fixture.entity, fixture.player));
        add(1, rayCast);
        assertEquals(1, fixture.entity.getInstancedAbilities().size());
        assertSame(rayCast, fixture.entity.getInstancedAbilities().get(0).getData());
    }

    @Test
    void initializationRetainsTheClientsHoldDamageOverride() throws Exception {
        add(
                3,
                rayCast,
                AbilityScalarValueEntry.newBuilder()
                        .setKey(AbilityString.newBuilder().setStr("Hold_Damage"))
                        .setFloatValue(2.5f)
                        .build());
        assertEquals(3, fixture.entity.getInstancedAbilities().size());
        assertNull(fixture.entity.getInstancedAbilities().get(0));
        assertNull(fixture.entity.getInstancedAbilities().get(1));
        assertEquals(
                2.5f,
                fixture.entity.getInstancedAbilities().get(2).getAbilitySpecials().getFloat("Hold_Damage"));
    }

    @Test
    void hashedParameterUpdateResolvesAgainstThisSkillsKnownSpecials() throws Exception {
        var ability = new Ability(rayCast, fixture.entity, fixture.player);
        fixture.entity.getInstancedAbilities().add(ability);
        fixture
                .player
                .getAbilityManager()
                .onAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(42)
                                .setHead(AbilityInvokeEntryHead.newBuilder().setInstancedAbilityId(1))
                                .setArgumentType(
                                        AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_OVERRIDE_PARAM)
                                .setAbilityData(
                                        AbilityScalarValueEntry.newBuilder()
                                                .setKey(
                                                        AbilityString.newBuilder().setHash(Utils.abilityHash("Hold_Damage")))
                                                .setFloatValue(3.75f)
                                                .build()
                                                .toByteString())
                                .build());
        assertEquals(3.75f, ability.getAbilitySpecials().getFloat("Hold_Damage"));
    }
}
