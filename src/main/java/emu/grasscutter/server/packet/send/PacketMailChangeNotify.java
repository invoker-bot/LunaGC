package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MailChangeNotifyOuterClass.MailChangeNotify;
import java.util.*;

public class PacketMailChangeNotify extends BasePacket {
    public PacketMailChangeNotify(Player player, Mail message) {
        super(PacketOpcodes.MailChangeNotify);
        setData(MailChangeNotify.newBuilder().addMailList(message.toProto(player)).build());
    }

    public PacketMailChangeNotify(Player player, List<Mail> changes) {
        super(PacketOpcodes.MailChangeNotify);
        setData(
                MailChangeNotify.newBuilder()
                        .addAllChangeMailList(changes.stream().map(m -> m.toProto(player)).toList())
                        .build());
    }

    public PacketMailChangeNotify(Player player, List<Mail> messages, List<Integer> deleted) {
        super(PacketOpcodes.MailChangeNotify);
        var proto = MailChangeNotify.newBuilder();
        if (messages != null)
            proto.addAllMailList(messages.stream().map(m -> m.toProto(player)).toList());
        if (deleted != null) proto.addAllDelMailIdList(deleted);
        setData(proto.build());
    }
}
