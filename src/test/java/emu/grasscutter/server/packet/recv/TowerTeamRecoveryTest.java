package emu.grasscutter.server.packet.recv;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.proto.TowerTeamOuterClass.TowerTeam;
import emu.grasscutter.net.proto.TowerTeamSelectReqOuterClass.TowerTeamSelectReq;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins TowerTeamSelectReq's 7.1 field numbers so abyss team selection remains parseable.
 *
 * <p>These cases build the payload by hand at those numbers rather than through the generated
 * builder, so a regeneration that dropped tower_team_list again would fail here instead of in game.
 */
public class TowerTeamRecoveryTest {

    private static final long AMBER = 4294967297L;
    private static final long KAEYA = 4294967298L;
    private static final long LISA = 4294967299L;
    private static final long BARBARA = 4294967300L;

    private static byte[] team(int teamId, long... guids) {
        var b = TowerTeam.newBuilder().setTowerTeamId(teamId);
        for (long g : guids) b.addAvatarGuidList(g);
        return b.build().toByteArray();
    }

    /** The 7.1 request puts teams at field 6 and the floor at field 3. */
    private static TowerTeamSelectReq onTheWire(int floorId, byte[]... teams) throws Exception {
        var baos = new ByteArrayOutputStream();
        var cos = CodedOutputStream.newInstance(baos);
        for (byte[] t : teams) cos.writeByteArray(6, t);
        cos.writeUInt32(3, floorId);
        cos.flush();
        return TowerTeamSelectReq.parseFrom(baos.toByteArray());
    }

    @Test
    @DisplayName("reads both halves of a two-team floor off the wire")
    public void readsTwoTeams() throws Exception {
        var req = onTheWire(1024, team(1, AMBER, KAEYA), team(2, LISA, BARBARA));

        assertEquals(1024, req.getFloorId());
        assertTrue(
                req.getUnknownFields().asMap().isEmpty(), "nothing left unparsed in the request");
        assertEquals(2, req.getTowerTeamListCount());
        assertEquals(List.of(AMBER, KAEYA), req.getTowerTeamList(0).getAvatarGuidListList());
        assertEquals(List.of(LISA, BARBARA), req.getTowerTeamList(1).getAvatarGuidListList());
        assertEquals(1, req.getTowerTeamList(0).getTowerTeamId());
        assertEquals(2, req.getTowerTeamList(1).getTowerTeamId());
    }

    @Test
    @DisplayName("keeps the wire order, which is what picks the second half")
    public void keepsWireOrder() throws Exception {
        // TowerManager enters the halves by index, so reordering here would seat the wrong team.
        var req = onTheWire(1039, team(2, LISA, BARBARA), team(1, AMBER, KAEYA));
        assertEquals(List.of(LISA, BARBARA), req.getTowerTeamList(0).getAvatarGuidListList());
        assertEquals(List.of(AMBER, KAEYA), req.getTowerTeamList(1).getAvatarGuidListList());
    }

    @Test
    @DisplayName("a one-team floor reads back as one team")
    public void readsOneTeam() throws Exception {
        var req = onTheWire(1001, team(1, AMBER, KAEYA, LISA, BARBARA));
        assertEquals(1, req.getTowerTeamListCount());
        assertEquals(4, req.getTowerTeamList(0).getAvatarGuidListCount());
    }

    @Test
    @DisplayName("floor_id is field 3 and not confused with the team list")
    public void floorIdUnchanged() throws Exception {
        var req = onTheWire(1025);
        assertEquals(1025, req.getFloorId());
        assertEquals(0, req.getTowerTeamListCount());
    }

    @Test
    @DisplayName("the mid-chamber team-change notify still builds and serialises to nothing")
    public void middleLevelChangeTeamNotifyLoads() {
        // The 7.1 dump still has no named source or CmdId for this empty message.
        var proto =
                emu.grasscutter.net.proto.TowerMiddleLevelChangeTeamNotifyOuterClass
                        .TowerMiddleLevelChangeTeamNotify.newBuilder()
                        .build();
        assertEquals(0, proto.toByteArray().length);
    }
}
