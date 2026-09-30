package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.entity.PlayerSkinOwned;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EquipSkinCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EquipSkinScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetWardrobeCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetWardrobeScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SkinInfo;
import cn.itcast.demo.mymmorpg.protocol.protobuf.UnequipSkinCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.UnequipSkinScRsp;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerSkinOwnedRepository;
import cn.itcast.demo.mymmorpg.skin.SkinConfig;
import cn.itcast.demo.mymmorpg.skin.SkinConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 皮肤衣柜与穿戴（对齐 MyLunarCore skin 域）：默认皮永久拥有，付费皮落库 player_skin_owned。
 */
@Service
public class SkinService {

    private final SkinConfigRepository skinConfigRepository;
    private final PlayerRepository playerRepository;
    private final PlayerSkinOwnedRepository playerSkinOwnedRepository;

    public SkinService(SkinConfigRepository skinConfigRepository,
                       PlayerRepository playerRepository,
                       PlayerSkinOwnedRepository playerSkinOwnedRepository) {
        this.skinConfigRepository = skinConfigRepository;
        this.playerRepository = playerRepository;
        this.playerSkinOwnedRepository = playerSkinOwnedRepository;
    }

    public ProtocolMessage handleGetWardrobe(long playerId, GetWardrobeCsReq req) {
        if (playerId <= 0) {
            return wardrobeRsp(RetCode.PLAYER_NOT_SELECTED, 0, List.of());
        }
        Optional<Player> opt = playerRepository.findById(playerId);
        if (opt.isEmpty()) {
            return wardrobeRsp(RetCode.PLAYER_NOT_FOUND, 0, List.of());
        }
        Player player = opt.get();
        ensureDefaultOwned(playerId);
        int equipped = normalizeEquipped(player.getEquippedSkinId());
        Set<Integer> owned = ownedSkinIds(playerId);
        List<SkinInfo> skins = skinConfigRepository.listEnabled().stream()
                .map(cfg -> toSkinInfo(cfg, owned, equipped))
                .toList();
        return wardrobeRsp(RetCode.OK, equipped, skins);
    }

    @Transactional
    public ProtocolMessage handleEquipSkin(long playerId, EquipSkinCsReq req) {
        if (playerId <= 0) {
            return equipRsp(RetCode.PLAYER_NOT_SELECTED, 0);
        }
        Optional<Player> opt = playerRepository.findById(playerId);
        if (opt.isEmpty()) {
            return equipRsp(RetCode.PLAYER_NOT_FOUND, 0);
        }
        Player player = opt.get();
        int skinId = req.getSkinId();
        SkinConfig cfg = skinConfigRepository.find(skinId);
        if (cfg == null || !cfg.isEnabled()) {
            return equipRsp(RetCode.SKIN_NOT_FOUND, normalizeEquipped(player.getEquippedSkinId()));
        }
        ensureDefaultOwned(playerId);
        if (!owns(playerId, cfg)) {
            return equipRsp(RetCode.SKIN_NOT_OWNED, normalizeEquipped(player.getEquippedSkinId()));
        }
        player.setEquippedSkinId(skinId);
        playerRepository.save(player);
        return equipRsp(RetCode.OK, skinId);
    }

    @Transactional
    public ProtocolMessage handleUnequipSkin(long playerId, UnequipSkinCsReq req) {
        if (playerId <= 0) {
            return unequipRsp(RetCode.PLAYER_NOT_SELECTED, 0);
        }
        Optional<Player> opt = playerRepository.findById(playerId);
        if (opt.isEmpty()) {
            return unequipRsp(RetCode.PLAYER_NOT_FOUND, 0);
        }
        Player player = opt.get();
        int defaultId = defaultSkinId();
        player.setEquippedSkinId(defaultId);
        playerRepository.save(player);
        return unequipRsp(RetCode.OK, defaultId);
    }

    /** 发放皮肤（活动/GM）；默认皮忽略；已拥有视为成功（幂等）。 */
    @Transactional
    public boolean grantSkin(long playerId, int skinId) {
        int rc = grantSkinRet(playerId, skinId);
        return rc == RetCode.OK || rc == RetCode.SKIN_ALREADY_OWNED;
    }

    /**
     * 发放皮肤并返回业务码：OK / SKIN_ALREADY_OWNED / SKIN_NOT_FOUND。
     * 默认皮视为无效发放目标（SKIN_NOT_FOUND）。
     */
    @Transactional
    public int grantSkinRet(long playerId, int skinId) {
        if (playerId <= 0 || skinId <= 0) {
            return RetCode.SKIN_NOT_FOUND;
        }
        SkinConfig cfg = skinConfigRepository.find(skinId);
        if (cfg == null || !cfg.isEnabled() || cfg.isDefault()) {
            return RetCode.SKIN_NOT_FOUND;
        }
        if (playerSkinOwnedRepository.existsByPlayerIdAndSkinId(playerId, skinId)) {
            return RetCode.SKIN_ALREADY_OWNED;
        }
        PlayerSkinOwned row = new PlayerSkinOwned();
        row.setPlayerId(playerId);
        row.setSkinId(skinId);
        row.setObtainedAt(System.currentTimeMillis());
        playerSkinOwnedRepository.save(row);
        return RetCode.OK;
    }

    /**
     * 按皮肤解锁道具 itemId 解锁：查 SkinConfig.itemId → grantSkin。
     */
    @Transactional
    public int unlockByItem(long playerId, int itemId) {
        if (playerId <= 0 || itemId <= 0) {
            return RetCode.SKIN_ITEM_INVALID;
        }
        SkinConfig cfg = skinConfigRepository.findByItemId(itemId);
        if (cfg == null || !cfg.isEnabled() || cfg.isDefault()) {
            return RetCode.SKIN_ITEM_INVALID;
        }
        return grantSkinRet(playerId, cfg.skinId());
    }

    private void ensureDefaultOwned(long playerId) {
        for (SkinConfig cfg : skinConfigRepository.listEnabled()) {
            if (cfg.isDefault() && !playerSkinOwnedRepository.existsByPlayerIdAndSkinId(playerId, cfg.skinId())) {
                PlayerSkinOwned row = new PlayerSkinOwned();
                row.setPlayerId(playerId);
                row.setSkinId(cfg.skinId());
                row.setObtainedAt(System.currentTimeMillis());
                playerSkinOwnedRepository.save(row);
            }
        }
    }

    private boolean owns(long playerId, SkinConfig cfg) {
        if (cfg.isDefault()) {
            return true;
        }
        return playerSkinOwnedRepository.existsByPlayerIdAndSkinId(playerId, cfg.skinId());
    }

    private Set<Integer> ownedSkinIds(long playerId) {
        Set<Integer> owned = new HashSet<>();
        for (SkinConfig cfg : skinConfigRepository.listEnabled()) {
            if (cfg.isDefault()) {
                owned.add(cfg.skinId());
            }
        }
        for (PlayerSkinOwned row : playerSkinOwnedRepository.findByPlayerId(playerId)) {
            owned.add(row.getSkinId());
        }
        return owned;
    }

    private int defaultSkinId() {
        return skinConfigRepository.listEnabled().stream()
                .filter(SkinConfig::isDefault)
                .map(SkinConfig::skinId)
                .findFirst()
                .orElse(0);
    }

    private int normalizeEquipped(Integer equipped) {
        int id = equipped == null ? 0 : equipped;
        if (id <= 0) {
            return defaultSkinId();
        }
        return id;
    }

    private static SkinInfo toSkinInfo(SkinConfig cfg, Set<Integer> owned, int equipped) {
        return SkinInfo.newBuilder()
                .setSkinId(cfg.skinId())
                .setName(cfg.getName() == null ? "" : cfg.getName())
                .setRarity(cfg.getRarity())
                .setResourceKey(cfg.getResourceKey() == null ? "" : cfg.getResourceKey())
                .setPreviewIcon(cfg.getPreviewIcon() == null ? "" : cfg.getPreviewIcon())
                .setObtainTips(cfg.getObtainTips() == null ? "" : cfg.getObtainTips())
                .setOwned(owned.contains(cfg.skinId()))
                .setEquipped(cfg.skinId() == equipped)
                .setIsDefault(cfg.isDefault())
                .build();
    }

    private static ProtocolMessage wardrobeRsp(int retcode, int equipped, List<SkinInfo> skins) {
        GetWardrobeScRsp.Builder b = GetWardrobeScRsp.newBuilder()
                .setRetcode(retcode)
                .setEquippedSkinId(equipped);
        b.addAllSkins(skins);
        return new ProtocolMessage(MessageId.GET_WARDROBE_SC_RSP, b.build().toByteArray());
    }

    private static ProtocolMessage equipRsp(int retcode, int equipped) {
        return new ProtocolMessage(MessageId.EQUIP_SKIN_SC_RSP,
                EquipSkinScRsp.newBuilder().setRetcode(retcode).setEquippedSkinId(equipped).build().toByteArray());
    }

    private static ProtocolMessage unequipRsp(int retcode, int equipped) {
        return new ProtocolMessage(MessageId.UNEQUIP_SKIN_SC_RSP,
                UnequipSkinScRsp.newBuilder().setRetcode(retcode).setEquippedSkinId(equipped).build().toByteArray());
    }
}
