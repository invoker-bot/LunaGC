package emu.grasscutter.game.avatar;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.game.player.BasePlayerManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.GrantReasonOuterClass.GrantReason;
import emu.grasscutter.server.event.entity.EntityCreationEvent;
import emu.grasscutter.server.packet.send.PacketAvatarChangeCostumeNotify;
import emu.grasscutter.server.packet.send.PacketAvatarFlycloakChangeNotify;
import emu.grasscutter.server.packet.send.PacketAvatarTraceEffectChangeNotify;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AvatarStorage extends BasePlayerManager implements Iterable<Avatar> {
    private final Int2ObjectMap<Avatar> avatars;
    private final Long2ObjectMap<Avatar> avatarsGuid;
    private final List<Avatar> legacyTrialAvatars = new ArrayList<>();

    public AvatarStorage(Player player) {
        super(player);
        this.avatars = new Int2ObjectOpenHashMap<>();
        this.avatarsGuid = new Long2ObjectOpenHashMap<>();
    }

    public Int2ObjectMap<Avatar> getAvatars() {
        return avatars;
    }

    public int getAvatarCount() {
        return this.avatars.size();
    }

    public Avatar getAvatarById(int id) {
        return getAvatars().get(id);
    }

    public Avatar getAvatarByGuid(long id) {
        return avatarsGuid.get(id);
    }

    public boolean hasAvatar(int id) {
        return getAvatars().containsKey(id);
    }

    public boolean addAvatar(Avatar avatar) {
        if (avatar.getTrialAvatarId() != 0
                || avatar.getAvatarData() == null
                || this.hasAvatar(avatar.getAvatarId())) {
            return false;
        }

        // Set owner first
        avatar.setOwner(getPlayer());

        // Put into maps
        this.avatars.put(avatar.getAvatarId(), avatar);
        this.avatarsGuid.put(avatar.getGuid(), avatar);

        avatar.save();

        return true;
    }

    public void addStartingWeapon(Avatar avatar) {
        // Make sure avatar owner is this player
        if (avatar.getPlayer() != this.getPlayer()) {
            return;
        }

        // Create weapon
        GameItem weapon = new GameItem(avatar.getAvatarData().getInitialWeapon());

        if (weapon.getItemData() != null) {
            this.getPlayer().getInventory().addItem(weapon);

            avatar.equipItem(weapon, true);
        }
    }

    public boolean wearFlycloak(long avatarGuid, int flycloakId) {
        Avatar avatar = this.getAvatarByGuid(avatarGuid);

        if (avatar == null || !getPlayer().getFlyCloakList().contains(flycloakId)) {
            return false;
        }

        avatar.setFlyCloak(flycloakId);
        avatar.save();

        // Update
        getPlayer().sendPacket(new PacketAvatarFlycloakChangeNotify(avatar));

        return true;
    }

    public boolean changeCostume(long avatarGuid, int costumeId) {
        Avatar avatar = this.getAvatarByGuid(avatarGuid);

        if (avatar == null) {
            return false;
        }

        if (costumeId != 0 && !getPlayer().getCostumeList().contains(costumeId)) {
            return false;
        }
        var costume = GameData.getAvatarCostumeDataMap().get(costumeId);
        if (costumeId != 0 && (costume == null || costume.getCharacterId() != avatar.getAvatarId()))
            return false;

        avatar.setCostume(costumeId);
        avatar.save();

        // Update entity
        EntityAvatar entity = avatar.getAsEntity();
        if (entity == null) {
            entity =
                    EntityCreationEvent.call(
                            EntityAvatar.class, new Class<?>[] {Avatar.class}, new Object[] {avatar});
            getPlayer().getWorld().broadcastPacket(new PacketAvatarChangeCostumeNotify(entity));
        } else {
            getPlayer().getWorld().broadcastPacket(new PacketAvatarChangeCostumeNotify(entity));
        }

        // Notify costume change to HomeWorld
        this.getPlayer().getHome().onPlayerChangedAvatarCostume(avatar);

        // Done
        return true;
    }

    public boolean changeTraceEffect(long avatarGuid, int traceEffectId) {
        Avatar avatar = this.getAvatarByGuid(avatarGuid);
        if (avatar == null
                || !this.getPlayer().getTraceEffectList().contains(traceEffectId) && traceEffectId != 0) {
            return false;
        }
        var trace = GameData.getAvatarTraceEffectDataMap().get(traceEffectId);
        if (traceEffectId != 0 && (trace == null || trace.getAvatarId() != avatar.getAvatarId()))
            return false;
        avatar.setTraceEffect(traceEffectId);
        avatar.save();
        EntityAvatar entity = avatar.getAsEntity();
        if (entity == null) {
            entity =
                    EntityCreationEvent.call(
                            EntityAvatar.class, new Class<?>[] {Avatar.class}, new Object[] {avatar});
            if (getPlayer().getWorld() != null)
                getPlayer().getWorld().broadcastPacket(new PacketAvatarTraceEffectChangeNotify(entity));
        } else {
            if (getPlayer().getWorld() != null)
                getPlayer().getWorld().broadcastPacket(new PacketAvatarTraceEffectChangeNotify(entity));
        }
        return true;
    }

    public boolean changeWeaponSkin(List<Long> guids, int skinId) {
        if (guids.isEmpty() || guids.size() > getAvatarCount()) return false;
        var skin = GameData.getAvatarWeaponSkinDataMap().get(skinId);
        if (skinId != 0 && (skin == null || !getPlayer().getWeaponSkinList().contains(skinId)))
            return false;
        var targets = new ArrayList<Avatar>();
        for (long guid : guids) {
            var avatar = getAvatarByGuid(guid);
            if (avatar == null
                    || avatar.getAvatarData() == null
                    || skinId != 0 && skin.getWeaponType() != avatar.getAvatarData().getWeaponType())
                return false;
            targets.add(avatar);
        }
        for (var avatar : targets) {
            avatar.setWeaponSkin(skinId);
            avatar.save();
        }
        getPlayer()
                .sendPacket(
                        new emu.grasscutter.server.packet.send.PacketAvatarWeaponSkinDataNotify(getPlayer()));
        // The refreshed team entities carry SceneAvatarInfo.weapon_skin_id for multiplayer viewers.
        if (getPlayer().getWorld() != null)
            getPlayer()
                    .getWorld()
                    .broadcastPacket(
                            new emu.grasscutter.server.packet.send.PacketSceneTeamUpdateNotify(getPlayer()));
        return true;
    }

    public void loadFromDatabase() {
        if (this.isLoaded()) return;

        List<Avatar> avatars = DatabaseHelper.getAvatars(getPlayer());

        for (Avatar avatar : avatars) {
            if (avatar.getTrialAvatarId() != 0) {
                this.legacyTrialAvatars.add(avatar);
                if (avatar.getGrantReason() == GrantReason.GRANT_REASON_BY_QUEST.getNumber()
                        && avatar.getFromParentQuestId() != 0)
                    this.getPlayer()
                            .getTeamManager()
                            .getQuestTrialAvatarIds()
                            .putIfAbsent(avatar.getTrialAvatarId(), avatar.getFromParentQuestId());
                continue;
            }
            // Should never happen
            if (avatar.getObjectId() == null) {
                continue;
            }

            AvatarData avatarData = GameData.getAvatarDataMap().get(avatar.getAvatarId());
            AvatarSkillDepotData skillDepot =
                    GameData.getAvatarSkillDepotDataMap().get(avatar.getSkillDepotId());
            if (avatarData == null || skillDepot == null) {
                continue;
            }

            // Set ownerships
            avatar.setAvatarData(avatarData);
            avatar.setSkillDepot(skillDepot);
            avatar.setOwner(getPlayer());

            // Force recalc of const boosted skills
            avatar.recalcConstellations();

            // Add to avatar storage
            this.avatars.put(avatar.getAvatarId(), avatar);
            this.avatarsGuid.put(avatar.getGuid(), avatar);

            // Set main character skill depot data, fixes loading with no element every login
            if ((avatar.getAvatarId() == 10000007) || (avatar.getAvatarId() == 10000005)) {
                avatar.setSkillDepot(skillDepot);
                avatar.setSkillDepotData(skillDepot);
                avatar.save();
            }
        }

        this.setLoaded(true);
    }

    public void postLoad() {
        for (Avatar avatar : this) {
            // Weapon check
            if (avatar.getWeapon() == null) {
                this.addStartingWeapon(avatar);
            }
            // Recalc stats
            avatar.recalcStats();
        }
    }

    protected Avatar createRecoveredAvatar(int avatarId) {
        return new Avatar(avatarId);
    }

    public void recoverLegacyTrialAvatars() {
        for (var avatar : new ArrayList<>(this.legacyTrialAvatars)) {
            if (avatar.getGrantReason() != GrantReason.GRANT_REASON_BY_QUEST.getNumber()) continue;
            var quest =
                    this.getPlayer().getQuestManager().getMainQuests().get(avatar.getFromParentQuestId());
            if (quest == null || !quest.isFinished()) continue;
            var mainData = GameData.getMainQuestDataMap().get(avatar.getFromParentQuestId());
            if (!this.hasAvatar(avatar.getAvatarId())
                    && mainData != null
                    && mainData.getRewardIdList() != null) {
                for (var rewardId : mainData.getRewardIdList()) {
                    var reward = GameData.getRewardDataMap().get(rewardId);
                    if (reward == null) continue;
                    for (var item : reward.getRewardItemList()) {
                        var data = GameData.getItemDataMap().get(item.getId());
                        if (item.getCount() > 0
                                && data != null
                                && data.getMaterialType() == MaterialType.MATERIAL_AVATAR
                                && item.getId() % 1000 + 10000000 == avatar.getAvatarId()
                                && !this.hasAvatar(avatar.getAvatarId())) {
                            var recovered = this.createRecoveredAvatar(avatar.getAvatarId());
                            if (!this.addAvatar(recovered)) continue;
                            // Inventory loads next and restores existing equipment; postLoad supplies
                            // a starter weapon only when there is no saved weapon to restore.
                            DatabaseHelper.saveGameSync(recovered);
                        }
                    }
                }
            }
            DatabaseHelper.deleteTrialAvatar(avatar);
            this.legacyTrialAvatars.remove(avatar);
        }
    }

    @Override
    public Iterator<Avatar> iterator() {
        return getAvatars().values().iterator();
    }
}
