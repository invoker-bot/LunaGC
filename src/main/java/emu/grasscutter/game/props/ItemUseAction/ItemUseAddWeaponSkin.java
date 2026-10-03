package emu.grasscutter.game.props.ItemUseAction;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.props.ItemUseOp;

public final class ItemUseAddWeaponSkin extends ItemUseInt {
    public ItemUseAddWeaponSkin(String[] params) {
        super(params);
    }

    @Override
    public ItemUseOp getItemUseOp() {
        return ItemUseOp.ITEM_USE_ADD_WEAPON_SKIN;
    }

    @Override
    public boolean useItem(UseItemParams params) {
        if (!GameData.getAvatarWeaponSkinDataMap().containsKey(i)) return false;
        params.player.addWeaponSkin(i);
        return true;
    }
}
