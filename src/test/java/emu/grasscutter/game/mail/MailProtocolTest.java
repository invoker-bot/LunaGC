package emu.grasscutter.game.mail;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.*;
import org.junit.jupiter.api.Test;

class MailProtocolTest {
    @Test
    void sevenOneResponsesUseObservedWireTags() {
        var descriptor = GetAllMailRspOuterClass.GetAllMailRsp.getDescriptor();
        assertEquals(15, descriptor.findFieldByName("mail_list").getNumber());
        assertEquals(14, descriptor.findFieldByName("is_collected").getNumber());
        assertEquals(7, descriptor.findFieldByName("is_truncated").getNumber());
        assertEquals(1, descriptor.findFieldByName("retcode").getNumber());
        var pages = GetAllMailResultNotifyOuterClass.GetAllMailResultNotify.getDescriptor();
        assertEquals(5, pages.findFieldByName("page_index").getNumber());
        assertEquals(12, pages.findFieldByName("total_page_count").getNumber());
    }

    @Test
    void requestsUseTheClientWritersRatherThanLegacyDescriptors() throws Exception {
        assertTrue(
                GetAllMailReqOuterClass.GetAllMailReq.parseFrom(new byte[] {16, 1}).getIsCollected());
        assertTrue(
                GetAllMailNotifyOuterClass.GetAllMailNotify.parseFrom(new byte[] {16, 1}).getIsCollected());
        assertEquals(5495, PacketOpcodes.GetAllMailReq);
        assertEquals(28960, PacketOpcodes.GetAllMailNotify);
        assertEquals(3404, PacketOpcodes.GetMailItemReq);
        assertEquals(3042, PacketOpcodes.DelMailReq);
        assertEquals(8592, PacketOpcodes.ReadMailNotify);
        assertEquals(
                7, DelMailReqOuterClass.DelMailReq.parseFrom(new byte[] {98, 1, 7}).getMailIdList(0));
        assertEquals(
                7,
                ReadMailNotifyOuterClass.ReadMailNotify.parseFrom(new byte[] {114, 1, 7}).getMailIdList(0));
    }
}
