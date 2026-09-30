package emu.grasscutter.game.talk;

import static emu.grasscutter.game.quest.enums.QuestCond.QUEST_COND_COMPLETE_TALK;
import static emu.grasscutter.game.quest.enums.QuestContent.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData.TalkData;
import emu.grasscutter.game.player.*;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.activity.salesman.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.event.player.PlayerNpcTalkEvent;
import lombok.NonNull;

public final class TalkManager extends BasePlayerManager {
    public TalkManager(@NonNull Player player) {
        super(player);
    }

    /**
     * Invoked when a talk is triggered.
     *
     * @param talkId The ID of the talk.
     * @param npcEntityId The entity ID of the NPC being talked to.
     */
    public void triggerTalkAction(int talkId, int npcEntityId) {
        var player = this.getPlayer();

        var talkData = GameData.getTalkConfigDataMap().get(talkId);

        // Invoke PlayerNpcTalkEvent.
        var event = new PlayerNpcTalkEvent(player, talkData, talkId, npcEntityId);
        if (!event.call()) return;

        if (talkData != null) {
            // Check if the NPC id is valid.
            var entity = player.getScene().getEntityById(npcEntityId);
            if (entity != null) {
                // The config ID of the entity is the NPC's ID.
                if (!talkData.getNpcId().contains(entity.getConfigId())) return;
            }

            // Execute the talk action on associated handlers.
            talkData
                    .getFinishExec()
                    .forEach(e -> player.getServer().getTalkSystem().triggerExec(player, talkData, e));

            // Save the talk value to the quest's data.
            this.saveTalkToQuest(talkId, talkData.getQuestId());
        }

        // Invoke the talking events for quests.
        var questManager = player.getQuestManager();
        questManager.queueEvent(QUEST_CONTENT_COMPLETE_ANY_TALK, talkId);
        questManager.queueEvent(QUEST_CONTENT_COMPLETE_TALK, talkId);
        questManager.queueEvent(QUEST_COND_COMPLETE_TALK, talkId);
    }

    public void saveTalkToQuest(int talkId, int mainQuestId) {
        // TODO, problem with this is that some talks for activity also have
        // quest id, which isn't present in QuestExcels
        var mainQuest = this.getPlayer().getQuestManager().getMainQuestById(mainQuestId);
        if (mainQuest == null) return;

        mainQuest.getTalks().put(talkId, new TalkData(talkId, ""));
    }

    /** Activity 5003 has no main quest document; its seven introductions belong to replay progress. */
    public int triggerSalesmanTalk(int talkId, int npcEntityId) {
        var player = getPlayer();
        var talk = GameData.getTalkConfigDataMap().get(talkId);
        if (!SalesmanTalk.isSalesmanTalk(talkId) || talk == null || talk.getQuestId() != SalesmanSchedule.ACTIVITY_ID
                || talk.getNpcId() == null || !talk.getNpcId().contains(SalesmanNpcScene.NPC_ID)) return Retcode.RET_NOT_CURRENT_TALK_VALUE;
        if (!new PlayerNpcTalkEvent(player, talk, talkId, npcEntityId).call()) return Retcode.RET_FAIL_VALUE;
        if (player.getScene() == null) return Retcode.RET_NOT_CURRENT_TALK_VALUE;
        try {
            int result = player.getScene().getSalesmanSceneController().completeTalk(player, talkId, System.currentTimeMillis());
            if (result == 0) {
                var quests = player.getQuestManager();
                quests.queueEvent(QUEST_CONTENT_COMPLETE_ANY_TALK, talkId);
                quests.queueEvent(QUEST_CONTENT_COMPLETE_TALK, talkId);
                quests.queueEvent(QUEST_COND_COMPLETE_TALK, talkId);
            }
            return result;
        } catch (RuntimeException failed) {
            Grasscutter.getLogger().error("Salesman talk failed for UID {} talk {}", player.getUid(), talkId, failed);
            return Retcode.RET_SVR_ERROR_VALUE;
        }
    }
}
