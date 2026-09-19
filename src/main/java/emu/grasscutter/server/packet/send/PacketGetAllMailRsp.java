package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.EquipParamOuterClass.EquipParam;
import emu.grasscutter.net.proto.GetAllMailRspOuterClass.GetAllMailRsp;
import emu.grasscutter.net.proto.MailDataOuterClass.MailData;
import emu.grasscutter.net.proto.MailItemOuterClass.MailItem;
import emu.grasscutter.net.proto.MailTextContentOuterClass.MailTextContent;
import java.util.*;

public class PacketGetAllMailRsp extends BasePacket {

    public PacketGetAllMailRsp(Player player, boolean isCollected) {
        super(PacketOpcodes.GetAllMailRsp);

        var proto = GetAllMailRsp.newBuilder();
        proto.setRetcode(0);
        proto.setIsCollected(isCollected);

        // The client echoes its requested tab through is_collected. LunaGC keeps a single inbox
        // rather than the collected/uncollected split, so every mail is returned for either tab
        // and the client re-derives the grouping from each MailData's own flags.
        for (Mail message : player.getMailHandler().getMail()) {
            var mailTextContent = MailTextContent.newBuilder();
            mailTextContent.setTitle(message.mailContent.title);
            mailTextContent.setContent(message.mailContent.content);
            mailTextContent.setSender(message.mailContent.sender);

            List<MailItem> mailItems = new ArrayList<>();
            for (Mail.MailItem item : message.itemList) {
                var mailItem = MailItem.newBuilder();
                var itemParam = EquipParam.newBuilder();
                itemParam.setItemId(item.itemId);
                itemParam.setItemNum(item.itemCount);
                mailItem.setEquipParam(itemParam.build());

                mailItems.add(mailItem.build());
            }

            var mailData = MailData.newBuilder();
            mailData.setMailId(player.getMailHandler().toClientMailId(player.getMailId(message)));
            mailData.setMailTextContent(mailTextContent.build());
            mailData.addAllItemList(mailItems);
            mailData.setSendTime((int) message.sendTime);
            mailData.setExpireTime((int) message.expireTime);
            mailData.setImportance(message.importance);
            mailData.setIsRead(message.isRead);
            mailData.setIsAttachmentGot(message.isAttachmentGot);
            mailData.setCollectStateValue(message.stateValue);

            proto.addMailList(mailData.build());
        }

        // No paging is implemented; the whole inbox always fits in one response.
        proto.setIsTruncated(false);

        this.setData(proto.build());
    }
}
