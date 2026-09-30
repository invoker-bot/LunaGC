package emu.grasscutter.scripts.data;

import java.util.List;
import lombok.Setter;

/** The crucible_config table on a historical scene gadget. */
@Setter
public final class SceneGadgetCrucibleConfig {
    public int duration;
    public int start_cd;
    public int mp_play_id;
    public List<Integer> progress_stage;

    public List<Integer> validatedStages() {
        if (duration <= 0 || start_cd < 0 || mp_play_id <= 0 || progress_stage == null
                || progress_stage.size() < 2 || progress_stage.get(0) != 0) {
            throw new IllegalArgumentException("Invalid crucible play configuration");
        }
        var stages = List.copyOf(progress_stage);
        for (int i = 1; i < stages.size(); i++) {
            if (stages.get(i) <= stages.get(i - 1))
                throw new IllegalArgumentException("Crucible stages must strictly increase");
        }
        return stages;
    }
}
