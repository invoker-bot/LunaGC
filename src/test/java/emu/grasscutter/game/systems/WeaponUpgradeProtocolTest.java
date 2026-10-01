package emu.grasscutter.game.systems;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.packet.recv.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeaponUpgradeProtocolTest {
  @Test
  void nativeWeaponUpgradeRequestIsRegistered() {
    // BAMJGGCALOL packet getter 0x14c9f9bd0 returns 0x5529.
    assertEquals(21801, PacketOpcodes.WeaponUpgradeReq);
    assertEquals(21801, HandlerWeaponUpgradeReq.class.getAnnotation(Opcodes.class).value());
  }

  @Test
  void nativeReturnPreviewRequestIsRegistered() {
    // GOEOJKJAIGF packet getter 0x14807dc30 returns 0x1be9.
    assertEquals(7145, PacketOpcodes.CalcWeaponUpgradeReturnItemsReq);
    assertEquals(
        7145, HandlerCalcWeaponUpgradeReturnItemsReq.class.getAnnotation(Opcodes.class).value());
  }

  @Test
  void upgradeReadsNativeGuidAndBothKindsOfMaterials() throws Exception {
    // Writer 0x14c9f97d0: target tag 0x28; codecs 0x7a (GUIDs), 0x42 (items).
    var req =
        WeaponUpgradeReqOuterClass.WeaponUpgradeReq.parseFrom(
            new byte[] {
              0x28,
              (byte) 0xac,
              2,
              0x7a,
              4,
              (byte) 0xad,
              2,
              (byte) 0xae,
              2,
              0x42,
              6,
              8,
              (byte) 0xcd,
              (byte) 0xac,
              6,
              0x10,
              3
            });
    assertEquals(300, req.getTargetWeaponGuid());
    assertEquals(List.of(301L, 302L), req.getFoodWeaponGuidListList());
    assertEquals(104013, req.getItemParamList(0).getItemId());
    assertEquals(3, req.getItemParamList(0).getCount());
  }

  @Test
  void previewReadsItsDistinctNativeMaterialFields() throws Exception {
    // Writer 0x14807dc40: target tag 0x08; codecs 0x72 (GUIDs), 0x52 (items).
    var req =
        CalcWeaponUpgradeReturnItemsReqOuterClass.CalcWeaponUpgradeReturnItemsReq.parseFrom(
            new byte[] {
              8,
              (byte) 0xac,
              2,
              0x72,
              4,
              (byte) 0xad,
              2,
              (byte) 0xae,
              2,
              0x52,
              6,
              8,
              (byte) 0xcd,
              (byte) 0xac,
              6,
              0x10,
              3
            });
    assertEquals(300, req.getTargetWeaponGuid());
    assertEquals(List.of(301L, 302L), req.getFoodWeaponGuidListList());
    assertEquals(104013, req.getItemParamList(0).getItemId());
    assertEquals(3, req.getItemParamList(0).getCount());
  }

  @Test
  void upgradeAndPreviewResponsesStillMatchNativeReaders() throws Exception {
    var upgrade =
        WeaponUpgradeRspOuterClass.WeaponUpgradeRsp.parseFrom(
            new byte[] {
              0x10,
              5,
              0x50,
              (byte) 0xac,
              2,
              0x48,
              1,
              0x70,
              2,
              0x22,
              6,
              8,
              (byte) 0xcd,
              (byte) 0xac,
              6,
              0x10,
              3
            });
    assertEquals(5, upgrade.getCurLevel());
    assertEquals(2, upgrade.getOldLevel());
    assertEquals(300, upgrade.getTargetWeaponGuid());
    assertEquals(1, upgrade.getRetcode());
    assertEquals(3, upgrade.getItemParamList(0).getCount());
    var preview =
        CalcWeaponUpgradeReturnItemsRspOuterClass.CalcWeaponUpgradeReturnItemsRsp.parseFrom(
            new byte[] {
              0x50, (byte) 0xac, 2, 0x30, 1, 0x1a, 6, 8, (byte) 0xcd, (byte) 0xac, 6, 0x10, 3
            });
    assertEquals(300, preview.getTargetWeaponGuid());
    assertEquals(1, preview.getRetcode());
    assertEquals(3, preview.getItemParamList(0).getCount());
  }
}
