# 7.1 weapon upgrade protocol

The supplied client's executable was read without modifying it. The SHA-256
profile is `7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e`,
the same profile used by `tools/extract_client_type_names.py`.

## Request recovery

| Message | Native type / opcode | Fields | Evidence |
| --- | --- | --- | --- |
| WeaponUpgradeReq | 64935 / BAMJGGCALOL / 21801 | target weapon GUID = 5, consumed weapon GUIDs = 15, material items = 8 | getter `0x14c9f9bd0` returns `0x5529`; writer `0x14c9f97d0` emits target tag `0x28`; codec initializer `0x14c9f9ac0` uses tags `0x7a`, `0x42` |
| CalcWeaponUpgradeReturnItemsReq | 76094 / GOEOJKJAIGF / 7145 | target weapon GUID = 1, consumed weapon GUIDs = 14, material items = 10 | getter `0x14807dc30` returns `0x1be9`; writer `0x14807dc40` emits target tag `0x08`; codec initializer `0x14807dec0` uses tags `0x72`, `0x52` |

Both requests share the native target-weapon field `CKDHOMKAJMI`, consumed-weapon
list `BHGMAKLCIFK`, and ItemParam list `BMLBGMCEKHM`. The upgrade sender at
`0x14ec0d710` creates type 64935, copies the target GUID into offset `0x28`,
appends consumed weapon GUIDs to offset `0x18`, and constructs material entries
for offset `0x20`. The preview sender at `0x14b14db60` creates type 76094 and
copies the target GUID and the two material lists into the corresponding offsets.

The previous server constants were negative placeholders (`-117`, `-118`).
`GameServerPacketHandler` excludes nonpositive opcodes from registration. The old
upgrade schema also used target field 6 / weapon-list field 2, and the preview
schema used weapon-list field 6 / material-list field 12. Replacing only the
opcodes would still lose the selected weapon or materials when parsing.

## Responses and materials

| Message | Opcode | Fields | Native reader |
| --- | --- | --- | --- |
| WeaponUpgradeRsp | 25465 | current level = 2, returned materials = 4, retcode = 9, target weapon GUID = 10, old level = 14 | `0x14c5d4840` |
| CalcWeaponUpgradeReturnItemsRsp | 5849 | returned materials = 3, retcode = 6, target weapon GUID = 10 | `0x14b0504b0` |

These response definitions already matched the native readers and were retained.
ItemParam's native reader `0x1478c4f30` uses item ID field 1 and count field 2.
The current resource ores are 104011 (400 EXP), 104012 (2,000 EXP), and 104013
(10,000 EXP), with `ITEM_USE_ADD_WEAPON_EXP` actions.

## Verification

`WeaponUpgradeProtocolTest` uses literal native wire fixtures for both requests
and responses, and verifies each request handler's opcode. Four of these checks
failed before the protocol fix.

`WeaponUpgradeTest` runs the real inventory and upgrade system using the resource
weapon, promotion and EXP tables. It verifies ore/Mora consumption, feeding a
weapon and retaining 80% of its accumulated EXP, preview without consumption,
refund equality after reaching the promotion cap, and insufficient funds without
inventory changes. Its UID 0 player does not persist items to the database.

These checks cover server parsing and settlement. Rendering and interaction in
the native game window require a client retest after deployment.
