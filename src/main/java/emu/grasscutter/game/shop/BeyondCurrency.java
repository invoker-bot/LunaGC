package emu.grasscutter.game.shop;

import emu.grasscutter.game.props.PlayerProperty;

/** 7.1 virtual-item switch at 0x14b504fd0, table at 0x14b505a7c. */
public final class BeyondCurrency {
    private BeyondCurrency() {}

    public static PlayerProperty propertyForItem(int itemId) {
        return switch (itemId) {
            case 231 -> PlayerProperty.PROP_PLAYER_BEYOND_MCOIN;
            case 232 -> PlayerProperty.PROP_PLAYER_BEYOND_COSTUME_GACHA_COIN;
            case 233 -> PlayerProperty.PROP_PLAYER_BEYOND_ATTENDANCE_COIN;
            case 234 -> PlayerProperty.PROP_PLAYER_BEYOND_COSTUME_TRANS_COIN;
            case 236 -> PlayerProperty.PROP_PLAYER_BEYOND_COSTUME_GACHA_FREE_COIN;
            case 241 -> PlayerProperty.PROP_PLAYER_BEYOND_DLC_COIN;
            case 242 -> PlayerProperty.PROP_PLAYER_BEYOND_DLC_FREE_COIN;
            case 244 -> PlayerProperty.PROP_PLAYER_BEYOND_FREE_MCOIN;
            default -> null;
        };
    }
}
