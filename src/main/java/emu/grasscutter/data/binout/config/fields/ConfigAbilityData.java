package emu.grasscutter.data.binout.config.fields;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class ConfigAbilityData {
    // Same hashed-dump spellings as ConfigEntityBase#abilities.
    @SerializedName(value = "abilityID", alternate = "FPLENFAEDIB")
    public String abilityID;

    @SerializedName(value = "abilityName", alternate = "KAJGDIHNKNE")
    public String abilityName;

    @SerializedName(value = "abilityOverride", alternate = "BIAMBCDKJKP")
    public String abilityOverride;
}
