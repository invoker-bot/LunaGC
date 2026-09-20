package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import java.util.Arrays;
import lombok.val;

/**
 * Switches the main character's skill depot, which is how the traveler gains an element.
 *
 * <p>The first parameter is a <b>depot index</b> into the avatar's {@code candSkillDepotIds},
 * matching {@link ElementType#getDepotIndex()} -- not the element's own ID. The opening chain
 * (main quest 2001) is the only content that uses this exec: sub 200101 passes {@code 3}, which is
 * Wind's depot index and resolves to depot 704 for the female traveler / 504 for the male, and
 * sub 200102 passes {@code 0} to hand the traveler back the element-less common depot for the
 * branch where they refuse the vision.
 *
 * <p>Without this, a quest-enabled account never receives an element at all: {@code
 * createDefaultTraveler} only pre-assigns depot 704 when questing is disabled, and with questing
 * on the traveler stays on the common depot (701/501, normal attack only, no burst) forever.
 */
@QuestValueExec(QuestExec.QUEST_EXEC_CHANGE_SKILL_DEPOT)
public final class ExecChangeSkillDepot extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        val depotIndex = Integer.parseInt(paramStr[0]);
        val owner = quest.getOwner();
        val mainAvatar = owner.getAvatars().getAvatarById(owner.getMainCharacterId());

        if (mainAvatar == null) {
            Grasscutter.getLogger()
                    .error("Failed to get main avatar for use {}", owner.getUid());
            return false;
        }

        // ElementType declares several members sharing depotIndex 0 (None/Frozen/Default); None is
        // declared first, so findFirst() settles the "back to the common depot" case on it.
        val targetElement =
                Arrays.stream(ElementType.values())
                        .filter(e -> e.getDepotIndex() == depotIndex)
                        .findFirst()
                        .orElse(ElementType.None);

        Grasscutter.getLogger()
                .debug(
                        "Changing avatar skill depot to index {} ({}) for quest {}.",
                        depotIndex,
                        targetElement.name(),
                        quest.getSubQuestId());
        return mainAvatar.changeElement(targetElement);
    }
}
