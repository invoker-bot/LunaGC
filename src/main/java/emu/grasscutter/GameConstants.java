package emu.grasscutter;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.utils.Utils;
import emu.grasscutter.utils.objects.SparseSet;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GameConstants {
    public static String VERSION = "7.0.0";
    public static int[] VERSION_PARTS = {7, 0, 0};
    public static boolean DEBUG = false;

    /*
     * Set by the '-dev' start-up argument. Developer mode is the server-side half of `task dev`:
     * it registers the {@link emu.grasscutter.server.dev.UnimplementedRequestReporter} listener,
     * which turns 'a request arrived that we probably do not implement' into a written report
     * instead of a silent no-op. Off by default because the no-response half of that check
     * measures every packet the session sends.
     */
    public static boolean DEVELOPER_MODE = false;

    public static final int ENTITY_ID_BIT_SHIFT = 21;
    public static final int DEFAULT_TEAMS = 4;
    public static final int MAX_TEAMS = 50;
    public static final int MAIN_CHARACTER_MALE = 10000005;
    public static final int MAIN_CHARACTER_FEMALE = 10000007;
    public static final Position START_POSITION = new Position(2747, 194, -1719);
    public static final int MAX_FRIENDS = 60;
    public static final int MAX_FRIEND_REQUESTS = 50;
    public static final int SERVER_CONSOLE_UID = 99;
    public static final int BATTLE_PASS_MAX_LEVEL = 50;
    public static final int BATTLE_PASS_POINT_PER_LEVEL = 1000;
    public static final int BATTLE_PASS_POINT_PER_WEEK = 10000;
    public static final int BATTLE_PASS_LEVEL_PRICE = 150;
    public static final int BATTLE_PASS_CURRENT_INDEX = 2;

    public static final String[] DEFAULT_ABILITY_STRINGS = {
        "Avatar_DefaultAbility_VisionReplaceDieInvincible",
         "Avatar_DefaultAbility_AvartarInShaderChange",
         "Avatar_SprintBS_Invincible",
         "Avatar_Freeze_Duration_Reducer",
         "Avatar_Attack_ReviveEnergy",
         "Avatar_Component_Initializer",
         "Avatar_FallAnthem_Achievement_Listener",
         "GrapplingHookSkill_Ability",
         "Avatar_PlayerBoy_DiveStamina_Reduction",
         "Ability_Avatar_Dive_SealEcho",
         "Absorb_SealEcho_Bullet_01",
         "Absorb_SealEcho_Bullet_02",
         "Ability_Avatar_Dive_CrabShield",
         "ActivityAbility_Absorb_Shoot",
         "SceneAbility_DiveVolume",
         "Ability_Avatar_Dive_Team",
         "Avatar_Absorb_TrackingMissile",
         "Avatar_Absorb_SwordFishSlash",
         "TeamAbility_Natsaurus_Transfer_Vehicle_Skill",
         "DynamicAbility_Phlogiston",
         "TeamAbility_Natsaurus_Vehicle_State_Listener",
         "DynamicAbility_ArcLight_Predicate",
         "DynamicAbility_CommonArcLight_Invincible_V5_0",
         "DynamicAbility_ArcLight_Wathcer",
         "SceneObj_Area_Nt_Property_Prop_YouLieDragon_Collision_DynamicAbility",
         "TeamAbility_Natsaurus_Preload_Drillhead",
         "TeamAbility_Natsaurus_Preload_Hookwalker",
         "TeamAbility_Natsaurus_Preload_Mosasaurus",
         "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet",
         "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_01",
         "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_02",
         "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_03",
         "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_04",
         "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_05",
         "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_06",
         "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_07",
         "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_5001",
         "Avatar_HDMesh_Controller",
         "Avatar_Trampoline_Jump_Controller",
         "Avatar_ArkheGrade_CD_Controller",
         "Avatar_FluidAgitator",
         "Avatar_TriggerNyxInstant",
         "Avatar_NyxState_Listener",
         "Avatar_StarSuperconductor_Field_Checker"
     };
      public static final String[] DEFAULT_TEAM_ABILITY_STRINGS = {
             "Ability_Avatar_Dive_Team",
             "TeamAbility_MoonPhase",

             "TeamAbility_Natsaurus_Transfer_Vehicle_Skill",
             "DynamicAbility_Phlogiston",
             "TeamAbility_Natsaurus_Vehicle_State_Listener",
             "DynamicAbility_ArcLight_Predicate",
             "DynamicAbility_CommonArcLight_Invincible_V5_0",
             "DynamicAbility_ArcLight_Wathcer",
             "SceneObj_Area_Nt_Property_Prop_YouLieDragon_Collision_DynamicAbility",
             "TeamAbility_Natsaurus_Preload_Drillhead",
             "TeamAbility_Natsaurus_Preload_Hookwalker",
             "TeamAbility_Natsaurus_Preload_Mosasaurus",
             "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet",
             "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_01",
             "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_02",
             "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_03",
             "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_04",
             "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_05",
             "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_06",
             "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_07",
             "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_5001"
     };
    public static final SparseSet ILLEGAL_WEAPONS = new SparseSet("""
        10000-10008, 11411, 11506-11508, 12505, 12506, 12508, 12509,
        13503, 13506, 14411, 14503, 14505, 14508, 15504-15506
        """);
    public static final SparseSet ILLEGAL_RELICS = new SparseSet("""
        20001, 23300-23340, 23383-23385, 78310-78554, 99310-99554
        """);
    public static final SparseSet ILLEGAL_ITEMS = new SparseSet("""
        100086, 100087, 100100-101000, 101106-101110, 101306, 101500-104000,
        105001, 105004, 106000-107000, 107011, 108000, 109000-110000,
        115000-130000, 200200-200899, 220050, 220054
        """);
    /**
     * Hashes of {@link #DEFAULT_ABILITY_STRINGS}.
     *
     * <p>Read these through {@link #defaultAbilityHashes()} rather than using the array directly.
     * The client resolves every embryo's hash against its own string table and raises an error
     * dialog for one it cannot resolve, so a name that no longer exists in this client version must
     * never be sent. These lists were carried forward from 6.x, which is how three renamed/removed
     * abilities reached a 7.0 client and kept throwing its error dialog; the accessor filters them
     * against the loaded ability data so a later drift costs a dropped embryo instead of a crash.
     */
    private static final int[] DEFAULT_ABILITY_HASHES =
            Arrays.stream(DEFAULT_ABILITY_STRINGS).mapToInt(Utils::abilityHash).toArray();

    public static int[] defaultAbilityHashes() {
        return filterKnownHashes(DEFAULT_ABILITY_HASHES);
    }

    /** Same guard as {@link #defaultAbilityHashes()} for the team list; the caller hashes it. */
    public static String[] defaultTeamAbilityStrings() {
        return filterKnownNames(DEFAULT_TEAM_ABILITY_STRINGS);
    }

    private static int[] filterKnownHashes(int[] hashes) {
        var known = GameData.getAbilityHashes();
        if (known.isEmpty()) return hashes; // Resources not loaded yet; empty means unknown, not none.
        var out = new int[hashes.length];
        int n = 0;
        for (int hash : hashes) {
            if (known.containsKey(hash)) out[n++] = hash;
            else reportDropped(hash);
        }
        return n == out.length ? out : Arrays.copyOf(out, n);
    }

    private static String[] filterKnownNames(String[] names) {
        var known = GameData.getAbilityHashes();
        if (known.isEmpty()) return names;
        var out = new String[names.length];
        int n = 0;
        for (String name : names) {
            if (known.containsKey(Utils.abilityHash(name))) out[n++] = name;
            else reportDropped(name);
        }
        return n == out.length ? out : Arrays.copyOf(out, n);
    }

    /** One warning per dead entry, so the next stale name surfaces once instead of once per avatar. */
    private static final Set<String> reportedDropped = ConcurrentHashMap.newKeySet();

    private static void reportDropped(Object what) {
        if (reportedDropped.add(String.valueOf(what))) {
            Grasscutter.getLogger()
                    .warn(
                            "Default ability {} is not in the loaded ability data; dropping it from the"
                                    + " embryo list because the client errors on hashes it cannot resolve.",
                            what);
        }
    }

    public static final int DEFAULT_ABILITY_NAME = Utils.abilityHash("Default");
}
