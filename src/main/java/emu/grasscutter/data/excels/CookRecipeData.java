package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import emu.grasscutter.data.ResourceType.LoadPriority;
import emu.grasscutter.data.common.ItemParamData;
import java.util.List;
import lombok.Getter;

@ResourceType(
        name = {"CookRecipeExcelConfigData.json"},
        loadPriority = LoadPriority.LOW)
@Getter
public class CookRecipeData extends GameResource {
    /** Slot count of the quality-tiered output list -- one slot per QTE quality tier. */
    private static final int QUALITY_OUTPUT_SLOT_COUNT = 3;

    @Getter(onMethod_ = @Override)
    private int id;

    private int rankLevel;
    private boolean isDefaultUnlocked;
    private int maxProficiency;

    private List<ItemParamData> qualityOutputVec;
    private List<ItemParamData> inputVec;
    private String qteParam;

    /*
     * The 7.0 client dump stores CookRecipeData's two repeated ItemParam lists under the wrong
     * JSON key for 254 of the 281 recipes: those entries carry the three quality-tier dish
     * outputs (consecutive dish ids, e.g. 108011/108012/108013 for 奇怪/普通/美味) in
     * "inputVec" and the ingredient list in "qualityOutputVec".  The remaining 27 newer
     * recipes are already the right way round.
     *
     * Both layouts are unambiguous -- the output list always occupies exactly three slots,
     * the ingredient list five -- so the exchange is detected and undone here rather than by
     * patching the gitignored resource dump.  Without this, cooking pays the finished dishes
     * as ingredients and hands out a raw ingredient (or an empty {id:0,count:0} slot) as the
     * result, which is why recipes appeared to produce nothing.
     */
    @Override
    public void onLoad() {
        boolean outputsAreInInputVec =
                this.inputVec != null
                        && this.inputVec.size() == QUALITY_OUTPUT_SLOT_COUNT
                        && (this.qualityOutputVec == null
                                || this.qualityOutputVec.size() != QUALITY_OUTPUT_SLOT_COUNT);

        if (outputsAreInInputVec) {
            var ingredients = this.qualityOutputVec;
            this.qualityOutputVec = this.inputVec;
            this.inputVec = ingredients;
        }
    }
}
