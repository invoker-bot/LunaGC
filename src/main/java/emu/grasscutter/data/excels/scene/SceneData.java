package emu.grasscutter.data.excels.scene;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import emu.grasscutter.game.props.SceneType;
import java.util.List;
import lombok.Getter;

@ResourceType(name = "SceneExcelConfigData.json")
@Getter
public final class SceneData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    @SerializedName("type")
    private SceneType sceneType;

    private String scriptData;
    private String levelEntityConfig;
    private List<Integer> specifiedAvatarList;

    // Scenes with a specifiedAvatarList name every variant of the forced character (both Travelers,
    // both Wanderers) but only ever admit maxSpecifiedAvatarNum of them - one, the variant the
    // player actually owns.
    private int maxSpecifiedAvatarNum;
}
