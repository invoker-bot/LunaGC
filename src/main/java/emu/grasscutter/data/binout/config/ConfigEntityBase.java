package emu.grasscutter.data.binout.config;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.binout.config.fields.*;
import java.util.Collection;
import javax.annotation.Nullable;
import lombok.Data;

@Data
public class ConfigEntityBase {
    @Nullable ConfigCommon configCommon;
    @Nullable ConfigCombat combat;

    // A few monster configs (103 of them, e.g. ConfigMonster_Dvalin_S00) come from a dump that
    // still hashes its field names; "IGPKOOHPBFG" is that dump's spelling of "abilities". Without
    // the alias the entry is skipped, and those monsters spawn without any of their config
    // abilities - which for Dvalin means the client never enters the DvalinS01FlyState play mode
    // and the AirGun the player is supposed to shoot the dragon with never shows up.
    @SerializedName(value = "abilities", alternate = "IGPKOOHPBFG")
    Collection<ConfigAbilityData> abilities;

    ConfigGlobalValue globalValue; // used for SGV in monsters and Gadgets
}
