package emu.grasscutter.game.gacha;

import dev.morphia.annotations.Entity;
import java.util.Arrays;
import lombok.Getter;

/** A chosen item and Fate Points belong to one banner run, independently of shared pity. */
@Entity
public class PlayerGachaWishInfo {
    private int scheduleId;
    private GachaBanner.BannerType bannerType;
    private int beginTime;
    private int[] featuredItems = {};
    @Getter private int wishItemId;
    @Getter private int fatePoints;

    public PlayerGachaWishInfo() {}

    public PlayerGachaWishInfo(GachaBanner banner) {
        this.scheduleId = banner.getScheduleId();
        this.bannerType = banner.getBannerType();
        this.beginTime = banner.getBeginTime();
        this.featuredItems = featuredItems(banner);
    }

    private static int[] featuredItems(GachaBanner banner) {
        return Arrays.stream(banner.getRateUpItems5()).distinct().sorted().toArray();
    }

    public boolean matches(GachaBanner banner) {
        return scheduleId == banner.getScheduleId()
                && bannerType == banner.getBannerType()
                && beginTime == banner.getBeginTime()
                && Arrays.equals(featuredItems, featuredItems(banner));
    }

    public void selectItem(int itemId) {
        if (itemId == 0 || wishItemId != itemId) fatePoints = 0;
        wishItemId = itemId;
    }

    public void recordFiveStar(int itemId, int maxProgress) {
        if (wishItemId == 0 || itemId == wishItemId) fatePoints = 0;
        else fatePoints = Math.min(fatePoints + 1, maxProgress);
    }
}
