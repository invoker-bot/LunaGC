package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.ResourceLoader.AvatarConfig;
import emu.grasscutter.data.ResourceLoader.OpenConfigData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityEmbryoEntry;
import emu.grasscutter.data.binout.OpenConfigEntry;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;

/** Ordinary avatars must not activate the dynamic skills reserved for quests or unlocks. */
class NahidaAbilityEmbryoTest {
    private final Map<String, AbilityData> previousAbilities = new HashMap<>();
    private final Map<String, OpenConfigEntry> previousOpenConfigs = new HashMap<>();
    private Map<String, AbilityEmbryoEntry> previousEmbryos;
    private Map<String, AvatarConfig> previousPlayerAbilities;
    private String[] configuredAbilities;

    @BeforeAll
    static void initializeRuntimePaths() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
    }

    @BeforeEach
    void loadRealNahidaResources() throws Exception {
        previousEmbryos = new HashMap<>(GameData.getAbilityEmbryoInfo());
        previousPlayerAbilities = GameDepot.getPlayerAbilities();
        for (var file :
                List.of(
                        "AvatarAbilities/ConfigAbility_Avatar_Nahida.json",
                        "QuestAbilities/ConfigAbility_Avatar_Quest.json")) {
            for (var config :
                    JsonUtils.loadToList(
                            Path.of("resources/BinOutput/Ability/Temp/" + file), AbilityConfigData.class)) {
                var ability = config.Default;
                if (ability == null || !ability.abilityName.startsWith("Avatar_Nahida_")) continue;
                ability.isDynamicAbility |= config.isDynamicAbility;
                previousAbilities.put(
                        ability.abilityName, GameData.getAbilityDataMap().put(ability.abilityName, ability));
            }
        }
        JsonUtils.loadToMap(
                        Path.of("resources/BinOutput/Talent/AvatarTalents/ConfigTalent_Nahida.json"),
                        String.class,
                        OpenConfigData[].class)
                .forEach(
                        (name, data) ->
                                previousOpenConfigs.put(
                                        name,
                                        GameData.getOpenConfigEntries().put(name, new OpenConfigEntry(name, data))));
        var avatarConfig =
                JsonUtils.loadToClass(
                        Path.of("resources/BinOutput/Avatar/ConfigAvatar_Nahida.json"), AvatarConfig.class);
        configuredAbilities =
                avatarConfig.abilities.stream().map(Object::toString).toArray(String[]::new);
        ResourceLoader.loadAbilityEmbryos();
    }

    @AfterEach
    void restoreResourceMaps() {
        GameData.getAbilityEmbryoInfo().clear();
        GameData.getAbilityEmbryoInfo().putAll(previousEmbryos);
        GameDepot.setPlayerAbilities(previousPlayerAbilities);
        previousAbilities.forEach(
                (name, data) -> {
                    if (data == null) GameData.getAbilityDataMap().remove(name);
                    else GameData.getAbilityDataMap().put(name, data);
                });
        previousOpenConfigs.forEach(
                (name, data) -> {
                    if (data == null) GameData.getOpenConfigEntries().remove(name);
                    else GameData.getOpenConfigEntries().put(name, data);
                });
    }

    private List<String> ordinaryAbilities() {
        return Arrays.asList(GameData.getAbilityEmbryoInfo().get("Nahida").getAbilities());
    }

    @Test
    void ordinaryNahidaKeepsTheConfiguredOrderWithoutBossRushSkills() {
        assertArrayEquals(
                configuredAbilities, GameData.getAbilityEmbryoInfo().get("Nahida").getAbilities());
    }

    @Test
    void normalEmbryoDoesNotUnlockPassivesOrConstellations() {
        assertFalse(ordinaryAbilities().contains("Avatar_Nahida_PermanentSkill_1"));
        assertFalse(ordinaryAbilities().contains("Avatar_Nahida_Constellation_6"));
    }

    @Test
    void unlockedTalentAddsOnlyItsExplicitAbilityOnce() {
        var avatar = new Avatar();
        assertTrue(avatar.getExtraAbilityEmbryos().isEmpty());
        avatar.addToExtraAbilityEmbryos("Nahida_Constellation_1");
        avatar.addToExtraAbilityEmbryos("Nahida_Constellation_1");
        var assigned = new ArrayList<>(ordinaryAbilities());
        assigned.addAll(avatar.getExtraAbilityEmbryos());
        assertEquals(1, Collections.frequency(assigned, "Avatar_Nahida_Constellation_1"));
        assertFalse(assigned.contains("Avatar_Nahida_Constellation_6"));
        assertFalse(assigned.contains("Avatar_Nahida_BossRush_SpecialArt"));
    }

    @Test
    void questSkillDefinitionRemainsAvailableWithoutActivatingIt() {
        var questSkill = GameData.getAbilityData("Avatar_Nahida_BossRush_SpecialArt");
        assertNotNull(questSkill);
        assertTrue(questSkill.isDynamicAbility);
        var entryAction = questSkill.modifiers.get("Exit_Handler").onAdded[0];
        assertEquals("_ABILITY_Nahida_IsInBossRush", entryAction.key);
        assertFalse(ordinaryAbilities().contains(questSkill.abilityName));
    }
}
