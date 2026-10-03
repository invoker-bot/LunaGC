package emu.grasscutter.game.gacha;

import dev.morphia.annotations.Entity;
import java.util.HashMap;
import java.util.Map;

@Entity
public class PlayerGachaInfo {
    private PlayerGachaBannerInfo standardBanner;
    private PlayerGachaBannerInfo beginnerBanner;
    private PlayerGachaBannerInfo eventCharacterBanner;
    private PlayerGachaBannerInfo eventWeaponBanner;
    private PlayerGachaBannerInfo chronicleBanner;
    private Map<String, PlayerGachaWishInfo> bannerWishes = new HashMap<>();

    public PlayerGachaInfo() {
        this.standardBanner = new PlayerGachaBannerInfo();
        this.eventCharacterBanner = new PlayerGachaBannerInfo();
        this.eventWeaponBanner = new PlayerGachaBannerInfo();
    }

    public PlayerGachaBannerInfo getStandardBanner() {
        if (this.standardBanner == null) this.standardBanner = new PlayerGachaBannerInfo();
        return this.standardBanner;
    }

    public PlayerGachaBannerInfo getBeginnerBanner() {
        if (this.beginnerBanner == null) this.beginnerBanner = new PlayerGachaBannerInfo();
        return this.beginnerBanner;
    }

    public PlayerGachaBannerInfo getEventCharacterBanner() {
        if (this.eventCharacterBanner == null) this.eventCharacterBanner = new PlayerGachaBannerInfo();
        return this.eventCharacterBanner;
    }

    public PlayerGachaBannerInfo getEventWeaponBanner() {
        if (this.eventWeaponBanner == null) this.eventWeaponBanner = new PlayerGachaBannerInfo();
        return this.eventWeaponBanner;
    }

    public PlayerGachaBannerInfo getChronicleBanner() {
        if (this.chronicleBanner == null) this.chronicleBanner = new PlayerGachaBannerInfo();
        return this.chronicleBanner;
    }

    public PlayerGachaBannerInfo getBannerInfo(GachaBanner banner) {
        return switch (banner.getBannerType()) {
            case STANDARD -> this.getStandardBanner();
            case BEGINNER -> this.getBeginnerBanner();
            case EVENT, CHARACTER, CHARACTER2 -> this.getEventCharacterBanner();
            case WEAPON -> this.getEventWeaponBanner();
            case CHRONICLE -> this.getChronicleBanner();
        };
    }

    public synchronized PlayerGachaWishInfo getWishInfo(GachaBanner banner) {
        if (!banner.hasEpitomized()) throw new IllegalArgumentException("Banner has no Epitomized Path");
        if (bannerWishes == null) bannerWishes = new HashMap<>();
        String key = banner.getBannerType().name() + ":" + banner.getScheduleId();
        var wish = bannerWishes.get(key);
        if (wish == null || !wish.matches(banner)) {
            // The old type-wide choice has no banner owner and cannot safely be migrated.
            // A rerun changes beginTime; merely extending endTime retains the current path.
            wish = new PlayerGachaWishInfo(banner);
            bannerWishes.put(key, wish);
        }
        if (wish.getWishItemId() != 0 && !banner.isWishItemAllowed(wish.getWishItemId())) {
            wish.selectItem(0);
        }
        return wish;
    }
}
