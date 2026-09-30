package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.entity.PlayerFriend;
import cn.itcast.demo.mymmorpg.entity.PlayerMail;
import cn.itcast.demo.mymmorpg.port.MailItemGrantPort;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.port.PlayerProgressPort;
import cn.itcast.demo.mymmorpg.protocol.BagRetCode;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AddFriendCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AddFriendScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimMailCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimMailScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.FriendInfo;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetFriendListCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetFriendListScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetMailListCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetMailListScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetRankingCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetRankingScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward;
import cn.itcast.demo.mymmorpg.protocol.protobuf.MailAttachment;
import cn.itcast.demo.mymmorpg.protocol.protobuf.MailInfo;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RankingEntry;
import cn.itcast.demo.mymmorpg.repository.PlayerFriendRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerMailRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import cn.itcast.demo.mymmorpg.support.RankingScoreStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 大厅服：好友/邮件落库，排行榜优先 Redis ZSET，miss 回退 player 表。
 */
@Service
public class HallService {

    private static final int RANKING_LEVEL = 1;
    private static final int RANKING_POWER = 2;
    private static final int WELCOME_MAIL_GOLD = 100;

    private final PlayerRepository playerRepository;
    private final PlayerFriendRepository playerFriendRepository;
    private final PlayerMailRepository playerMailRepository;
    private final ObjectProvider<PlayerProgressPort> playerProgressPort;
    private final ObjectProvider<PlayerCachePort> playerCachePort;
    private final ObjectProvider<MailItemGrantPort> mailItemGrantPort;
    private final PlayerPresenceQuery playerPresenceQuery;
    private final RankingScoreStore rankingScoreStore;
    private final ObjectMapper objectMapper;

    public HallService(
            PlayerRepository playerRepository,
            PlayerFriendRepository playerFriendRepository,
            PlayerMailRepository playerMailRepository,
            ObjectProvider<PlayerProgressPort> playerProgressPort,
            ObjectProvider<PlayerCachePort> playerCachePort,
            ObjectProvider<MailItemGrantPort> mailItemGrantPort,
            PlayerPresenceQuery playerPresenceQuery,
            RankingScoreStore rankingScoreStore,
            ObjectMapper objectMapper) {
        this.playerRepository = playerRepository;
        this.playerFriendRepository = playerFriendRepository;
        this.playerMailRepository = playerMailRepository;
        this.playerProgressPort = playerProgressPort;
        this.playerCachePort = playerCachePort;
        this.mailItemGrantPort = mailItemGrantPort;
        this.playerPresenceQuery = playerPresenceQuery;
        this.rankingScoreStore = rankingScoreStore;
        this.objectMapper = objectMapper;
    }

    public ProtocolMessage handleGetFriendList(long playerId, GetFriendListCsReq req) {
        if (playerId <= 0) {
            return friendListRsp(RetCode.PLAYER_NOT_SELECTED, List.of());
        }
        List<FriendInfo> friends = new ArrayList<>();
        for (PlayerFriend rel : playerFriendRepository.findByPlayerId(playerId)) {
            FriendInfo info = toFriendInfo(rel.getFriendId());
            if (info != null) {
                friends.add(info);
            }
        }
        return friendListRsp(RetCode.OK, friends);
    }

    @Transactional
    public ProtocolMessage handleAddFriend(long playerId, AddFriendCsReq req) {
        if (playerId <= 0) {
            return addFriendRsp(RetCode.PLAYER_NOT_SELECTED, null);
        }
        long targetId = req.getTargetPlayerId();
        if (targetId <= 0 || targetId == playerId) {
            return addFriendRsp(RetCode.FRIEND_NOT_FOUND, null);
        }
        if (!playerRepository.existsById(targetId)) {
            return addFriendRsp(RetCode.PLAYER_NOT_FOUND, null);
        }
        if (playerFriendRepository.existsByPlayerIdAndFriendId(playerId, targetId)) {
            return addFriendRsp(RetCode.FRIEND_ALREADY_EXISTS, null);
        }
        long now = System.currentTimeMillis();
        playerFriendRepository.save(newFriend(playerId, targetId, now));
        if (!playerFriendRepository.existsByPlayerIdAndFriendId(targetId, playerId)) {
            playerFriendRepository.save(newFriend(targetId, playerId, now));
        }
        return addFriendRsp(RetCode.OK, toFriendInfo(targetId));
    }

    @Transactional
    public ProtocolMessage handleGetMailList(long playerId, GetMailListCsReq req) {
        if (playerId <= 0) {
            return mailListRsp(RetCode.PLAYER_NOT_SELECTED, List.of());
        }
        ensureWelcomeMail(playerId);
        List<MailInfo> infos = new ArrayList<>();
        for (PlayerMail mail : playerMailRepository.findByPlayerIdOrderByIdAsc(playerId)) {
            infos.add(toMailInfo(mail));
        }
        return mailListRsp(RetCode.OK, infos);
    }

    @Transactional
    public ProtocolMessage handleClaimMail(long playerId, ClaimMailCsReq req) {
        if (playerId <= 0) {
            return claimMailRsp(RetCode.PLAYER_NOT_SELECTED, 0, 0);
        }
        Optional<PlayerMail> opt = playerMailRepository.findByIdAndPlayerId(req.getMailId(), playerId);
        if (opt.isEmpty()) {
            return claimMailRsp(RetCode.MAIL_NOT_FOUND, req.getMailId(), 0);
        }
        PlayerMail target = opt.get();
        if (Boolean.TRUE.equals(target.getClaimed())) {
            return claimMailRsp(RetCode.MAIL_ALREADY_CLAIMED, target.getId(), 0);
        }
        List<ItemReward> itemRewards = parseItemRewards(target.getAttachmentsJson());
        if (!itemRewards.isEmpty()) {
            MailItemGrantPort grant = mailItemGrantPort.getIfAvailable();
            if (grant == null) {
                return claimMailRsp(RetCode.INTERNAL_ERROR, target.getId(), 0);
            }
            int rc = grant.grantItemsForMail(playerId, target.getId(), itemRewards);
            if (rc != BagRetCode.OK) {
                return claimMailRsp(RetCode.INTERNAL_ERROR, target.getId(), 0);
            }
        }
        target.setClaimed(true);
        playerMailRepository.save(target);
        int goldGained = target.getGold() == null ? 0 : target.getGold();
        if (goldGained > 0) {
            PlayerProgressPort progress = playerProgressPort.getIfAvailable();
            if (progress != null) {
                Player player = resolvePlayer(playerId);
                if (player != null) {
                    progress.addGold(player, goldGained);
                }
            }
        }
        return claimMailRsp(RetCode.OK, target.getId(), goldGained);
    }

    public ProtocolMessage handleGetRanking(long playerId, GetRankingCsReq req) {
        if (playerId <= 0) {
            return rankingRsp(RetCode.PLAYER_NOT_SELECTED, req.getRankingType(), List.of());
        }
        int rankingType = req.getRankingType() == 0 ? RANKING_LEVEL : req.getRankingType();
        int limit = req.getLimit() <= 0 ? 50 : Math.min(req.getLimit(), 50);
        List<RankingEntry> fromRedis = rankingFromRedis(rankingType, limit);
        if (!fromRedis.isEmpty()) {
            return rankingRsp(RetCode.OK, rankingType, fromRedis);
        }
        List<Player> players = rankingType == RANKING_POWER
                ? playerRepository.findTop50ByOrderByPowerScoreDesc()
                : playerRepository.findTop50ByOrderByLevelDesc();
        List<RankingEntry> entries = new ArrayList<>();
        int rank = 1;
        for (Player p : players) {
            if (rank > limit) {
                break;
            }
            if (p.getId() != null) {
                rankingScoreStore.updatePlayer(
                        p.getId(),
                        p.getLevel() == null ? 1 : p.getLevel(),
                        p.getPowerScore() == null ? 0 : p.getPowerScore());
            }
            entries.add(RankingEntry.newBuilder()
                    .setRank(rank++)
                    .setPlayerId(p.getId() == null ? 0L : p.getId())
                    .setPlayerName(p.getName() == null ? "" : p.getName())
                    .setLevel(p.getLevel() == null ? 1 : p.getLevel())
                    .setPowerScore(p.getPowerScore() == null ? 0 : p.getPowerScore())
                    .build());
        }
        return rankingRsp(RetCode.OK, rankingType, entries);
    }

    private List<RankingEntry> rankingFromRedis(int rankingType, int limit) {
        if (rankingScoreStore == null || !rankingScoreStore.available()) {
            return List.of();
        }
        String key = rankingType == RANKING_POWER ? RankingScoreStore.KEY_POWER : RankingScoreStore.KEY_LEVEL;
        List<RankingScoreStore.Entry> top = rankingScoreStore.top(key, limit);
        if (top.isEmpty()) {
            return List.of();
        }
        List<RankingEntry> entries = new ArrayList<>();
        int rank = 1;
        for (RankingScoreStore.Entry e : top) {
            Player p = resolvePlayer(e.playerId());
            int level = p == null || p.getLevel() == null ? (int) e.score() : p.getLevel();
            int power = p == null || p.getPowerScore() == null ? (int) e.score() : p.getPowerScore();
            if (rankingType == RANKING_LEVEL) {
                level = (int) e.score();
            } else {
                power = (int) e.score();
            }
            entries.add(RankingEntry.newBuilder()
                    .setRank(rank++)
                    .setPlayerId(e.playerId())
                    .setPlayerName(p == null || p.getName() == null ? "player-" + e.playerId() : p.getName())
                    .setLevel(level)
                    .setPowerScore(power)
                    .build());
        }
        return entries;
    }

    private void ensureWelcomeMail(long playerId) {
        if (playerMailRepository.countByPlayerId(playerId) > 0) {
            return;
        }
        PlayerMail welcome = new PlayerMail();
        welcome.setPlayerId(playerId);
        welcome.setTitle("欢迎来到冒险世界");
        welcome.setBody("这是一封欢迎邮件，领取后可获得金币奖励。");
        welcome.setClaimed(false);
        welcome.setCreatedAt(System.currentTimeMillis());
        welcome.setGold(WELCOME_MAIL_GOLD);
        playerMailRepository.save(welcome);
    }

    /**
     * 系统发信（深渊里程碑等）。attachmentsJson 形如 [{"itemId":10002,"count":50}]。
     */
    @Transactional
    public PlayerMail sendSystemMail(long playerId, String title, String body, String attachmentsJson) {
        if (playerId <= 0) {
            throw new IllegalArgumentException("playerId required");
        }
        PlayerMail mail = new PlayerMail();
        mail.setPlayerId(playerId);
        mail.setTitle(title == null || title.isBlank() ? "系统邮件" : title);
        mail.setBody(body == null ? "" : body);
        mail.setClaimed(false);
        mail.setCreatedAt(System.currentTimeMillis());
        mail.setGold(0);
        mail.setAttachmentsJson(attachmentsJson);
        return playerMailRepository.save(mail);
    }

    private List<ItemReward> parseItemRewards(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<>() {
            });
            List<ItemReward> out = new ArrayList<>();
            for (Map<String, Object> m : raw) {
                int itemId = ((Number) m.get("itemId")).intValue();
                int count = ((Number) m.get("count")).intValue();
                if (itemId > 0 && count > 0) {
                    out.add(ItemReward.newBuilder().setItemId(itemId).setCount(count).build());
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static PlayerFriend newFriend(long playerId, long friendId, long createdAt) {
        PlayerFriend rel = new PlayerFriend();
        rel.setPlayerId(playerId);
        rel.setFriendId(friendId);
        rel.setCreatedAt(createdAt);
        return rel;
    }

    private Player resolvePlayer(long playerId) {
        PlayerCachePort cache = playerCachePort.getIfAvailable();
        if (cache != null) {
            Player cached = cache.findById(playerId);
            if (cached != null) {
                return cached;
            }
        }
        return playerRepository.findById(playerId).orElse(null);
    }

    private FriendInfo toFriendInfo(long friendId) {
        boolean online = playerPresenceQuery != null && playerPresenceQuery.isOnline(friendId);
        Optional<Player> opt = playerRepository.findById(friendId);
        if (opt.isEmpty()) {
            return FriendInfo.newBuilder()
                    .setPlayerId(friendId)
                    .setPlayerName("player-" + friendId)
                    .setLevel(1)
                    .setOnline(online)
                    .build();
        }
        Player p = opt.get();
        return FriendInfo.newBuilder()
                .setPlayerId(friendId)
                .setPlayerName(p.getName() == null ? "" : p.getName())
                .setLevel(p.getLevel() == null ? 1 : p.getLevel())
                .setOnline(online)
                .build();
    }

    private MailInfo toMailInfo(PlayerMail mail) {
        MailInfo.Builder b = MailInfo.newBuilder()
                .setMailId(mail.getId() == null ? 0L : mail.getId())
                .setTitle(mail.getTitle())
                .setBody(mail.getBody())
                .setClaimed(Boolean.TRUE.equals(mail.getClaimed()))
                .setCreatedAt(mail.getCreatedAt() == null ? 0L : mail.getCreatedAt());
        int gold = mail.getGold() == null ? 0 : mail.getGold();
        if (gold > 0) {
            b.addAttachments(MailAttachment.newBuilder().setGold(gold).build());
        }
        for (ItemReward r : parseItemRewards(mail.getAttachmentsJson())) {
            b.addAttachments(MailAttachment.newBuilder()
                    .setItemId(r.getItemId())
                    .setCount(r.getCount())
                    .build());
        }
        return b.build();
    }

    private static ProtocolMessage friendListRsp(int retcode, List<FriendInfo> friends) {
        GetFriendListScRsp.Builder b = GetFriendListScRsp.newBuilder().setRetcode(retcode);
        b.addAllFriends(friends);
        return new ProtocolMessage(MessageId.GET_FRIEND_LIST_SC_RSP, b.build().toByteArray());
    }

    private static ProtocolMessage addFriendRsp(int retcode, FriendInfo friend) {
        AddFriendScRsp.Builder b = AddFriendScRsp.newBuilder().setRetcode(retcode);
        if (friend != null) {
            b.setFriend(friend);
        }
        return new ProtocolMessage(MessageId.ADD_FRIEND_SC_RSP, b.build().toByteArray());
    }

    private static ProtocolMessage mailListRsp(int retcode, List<MailInfo> mails) {
        GetMailListScRsp.Builder b = GetMailListScRsp.newBuilder().setRetcode(retcode);
        b.addAllMails(mails);
        return new ProtocolMessage(MessageId.GET_MAIL_LIST_SC_RSP, b.build().toByteArray());
    }

    private static ProtocolMessage claimMailRsp(int retcode, long mailId, int goldGained) {
        ClaimMailScRsp body = ClaimMailScRsp.newBuilder()
                .setRetcode(retcode)
                .setMailId(mailId)
                .setGoldGained(goldGained)
                .build();
        return new ProtocolMessage(MessageId.CLAIM_MAIL_SC_RSP, body.toByteArray());
    }

    private static ProtocolMessage rankingRsp(int retcode, int rankingType, List<RankingEntry> entries) {
        GetRankingScRsp.Builder b = GetRankingScRsp.newBuilder()
                .setRetcode(retcode)
                .setRankingType(rankingType);
        b.addAllEntries(entries);
        return new ProtocolMessage(MessageId.GET_RANKING_SC_RSP, b.build().toByteArray());
    }
}
