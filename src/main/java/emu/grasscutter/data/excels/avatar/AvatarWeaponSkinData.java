package emu.grasscutter.data.excels.avatar;

import emu.grasscutter.data.*;
import emu.grasscutter.game.props.WeaponType;
import lombok.Getter;

@Getter
@ResourceType(name = "AvatarWeaponSkinExcelConfigData.json")
public final class AvatarWeaponSkinData extends GameResource {
    private int weaponSkinId;
    private int itemId;
    private WeaponType weaponType;
    private long nameTextMapHash;

    @Override
    public int getId() {
        return weaponSkinId;
    }
}
