package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AllocateTalentCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AllocateTalentScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CreatePlayerCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CreatePlayerScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetCharacterInfoCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetCharacterInfoScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.PlayerProfile;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SpendGoldCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SpendGoldScRsp;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import cn.itcast.demo.mymmorpg.support.RankingScoreStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 角色创建、属性/天赋查询与分配、金币消耗（经济系统）。
 */
@Service
public class CharacterService {

    public static final int MAX_PLAYERS_PER_ACCOUNT = 5;
    public static final int NAME_MIN = 2;
    public static final int NAME_MAX = 16;

    private final PlayerRepository playerRepository;
    private final PlayerEntityCacheService playerEntityCacheService;
    private final PlayerProgressService playerProgressService;
    private final RankingScoreStore rankingScoreStore;
    private final ObjectMapper objectMapper;

    public CharacterService(
            PlayerRepository playerRepository,
            PlayerEntityCacheService playerEntityCacheService,
            PlayerProgressService playerProgressService,
            RankingScoreStore rankingScoreStore,
            ObjectMapper objectMapper) {
        this.playerRepository = playerRepository;
        this.playerEntityCacheService = playerEntityCacheService;
        this.playerProgressService = playerProgressService;
        this.rankingScoreStore = rankingScoreStore;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProtocolMessage handleCreatePlayer(CreatePlayerCsReq req, long boundAccountId) {
        if (boundAccountId <= 0 || req.getAccountId() != boundAccountId) {
            return createRsp(RetCode.NOT_LOGGED_IN, null);
        }
        String name = req.getPlayerName() == null ? "" : req.getPlayerName().trim();
        if (name.length() < NAME_MIN || name.length() > NAME_MAX) {
            return createRsp(RetCode.INTERNAL_ERROR, null);
        }
        if (playerRepository.countByAccountId(boundAccountId) >= MAX_PLAYERS_PER_ACCOUNT) {
            return createRsp(RetCode.PLAYER_CREATE_LIMIT, null);
        }
        if (playerRepository.existsByName(name)) {
            return createRsp(RetCode.PLAYER_NAME_TAKEN, null);
        }
        Player p = new Player();
        p.setAccountId(boundAccountId);
        p.setName(name);
        p.setLevel(1);
        p.setExp(0L);
        p.setGold(0L);
        p.setStrength(10);
        p.setAgility(10);
        p.setIntelligence(10);
        p.setTalentPoints(0);
        p.setTalentJson("{}");
        p.recalcPowerScore();
        Player saved = playerRepository.save(p);
        playerEntityCacheService.saveCacheAndMarkDirty(saved);
        rankingScoreStore.updatePlayer(
                saved.getId(),
                saved.getLevel() == null ? 1 : saved.getLevel(),
                saved.getPowerScore() == null ? 0 : saved.getPowerScore());
        PlayerProfile profile = PlayerProfile.newBuilder()
                .setPlayerId(saved.getId())
                .setPlayerName(saved.getName())
                .setLevel(saved.getLevel())
                .build();
        return createRsp(RetCode.OK, profile);
    }

    @Transactional(readOnly = true)
    public ProtocolMessage handleGetCharacterInfo(long playerId, GetCharacterInfoCsReq req) {
        if (playerId <= 0) {
            return charInfoRsp(RetCode.PLAYER_NOT_SELECTED, null);
        }
        Player p = playerEntityCacheService.findById(playerId);
        if (p == null) {
            return charInfoRsp(RetCode.PLAYER_NOT_FOUND, null);
        }
        return charInfoRsp(RetCode.OK, p);
    }

    @Transactional
    public ProtocolMessage handleAllocateTalent(long playerId, AllocateTalentCsReq req) {
        if (playerId <= 0) {
            return talentRsp(RetCode.PLAYER_NOT_SELECTED, 0, 0, 0, 0);
        }
        Player p = playerEntityCacheService.findById(playerId);
        if (p == null) {
            return talentRsp(RetCode.PLAYER_NOT_FOUND, 0, 0, 0, 0);
        }
        int points = req.getPoints() <= 0 ? 1 : (int) req.getPoints();
        int available = p.getTalentPoints() == null ? 0 : p.getTalentPoints();
        if (points > available) {
            return talentRsp(RetCode.TALENT_POINTS_NOT_ENOUGH, req.getTalentId(), 0, available, p.getPowerScore());
        }
        Map<Integer, Integer> talents = parseTalents(p.getTalentJson());
        int talentId = req.getTalentId();
        int level = talents.getOrDefault(talentId, 0) + points;
        talents.put(talentId, level);
        p.setTalentPoints(available - points);
        // 天赋 1/2/3 分别加力量/敏捷/智力
        if (talentId == 1) {
            p.setStrength((p.getStrength() == null ? 10 : p.getStrength()) + points);
        } else if (talentId == 2) {
            p.setAgility((p.getAgility() == null ? 10 : p.getAgility()) + points);
        } else if (talentId == 3) {
            p.setIntelligence((p.getIntelligence() == null ? 10 : p.getIntelligence()) + points);
        }
        p.setTalentJson(writeTalents(talents));
        p.recalcPowerScore();
        Player saved = playerEntityCacheService.saveCacheAndMarkDirty(p);
        rankingScoreStore.updatePlayer(
                saved.getId(),
                saved.getLevel() == null ? 1 : saved.getLevel(),
                saved.getPowerScore() == null ? 0 : saved.getPowerScore());
        return talentRsp(RetCode.OK, talentId, level, saved.getTalentPoints(), saved.getPowerScore());
    }

    @Transactional
    public ProtocolMessage handleSpendGold(long playerId, SpendGoldCsReq req) {
        if (playerId <= 0) {
            return spendRsp(RetCode.PLAYER_NOT_SELECTED, 0);
        }
        Player p = playerEntityCacheService.findById(playerId);
        if (p == null) {
            return spendRsp(RetCode.PLAYER_NOT_FOUND, 0);
        }
        long amount = req.getAmount();
        Player updated = playerProgressService.spendGold(p, amount);
        if (updated == null) {
            return spendRsp(RetCode.GOLD_NOT_ENOUGH, p.getGold() == null ? 0 : p.getGold());
        }
        return spendRsp(RetCode.OK, updated.getGold());
    }

    private Map<Integer, Integer> parseTalents(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            Map<String, Integer> raw = objectMapper.readValue(json, new TypeReference<>() {});
            Map<Integer, Integer> out = new HashMap<>();
            raw.forEach((k, v) -> out.put(Integer.parseInt(k), v));
            return out;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String writeTalents(Map<Integer, Integer> talents) {
        try {
            Map<String, Integer> raw = new HashMap<>();
            talents.forEach((k, v) -> raw.put(String.valueOf(k), v));
            return objectMapper.writeValueAsString(raw);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static ProtocolMessage createRsp(int retcode, PlayerProfile profile) {
        var b = CreatePlayerScRsp.newBuilder().setRetcode(retcode);
        if (profile != null) {
            b.setPlayer(profile);
        }
        return new ProtocolMessage(MessageId.CREATE_PLAYER_SC_RSP, b.build().toByteArray());
    }

    private ProtocolMessage charInfoRsp(int retcode, Player p) {
        var b = GetCharacterInfoScRsp.newBuilder().setRetcode(retcode);
        if (p != null) {
            b.setPlayerId(p.getId())
                    .setPlayerName(p.getName() == null ? "" : p.getName())
                    .setLevel(p.getLevel() == null ? 1 : p.getLevel())
                    .setExp(p.getExp() == null ? 0 : p.getExp())
                    .setGold(p.getGold() == null ? 0 : p.getGold())
                    .setStrength(p.getStrength() == null ? 10 : p.getStrength())
                    .setAgility(p.getAgility() == null ? 10 : p.getAgility())
                    .setIntelligence(p.getIntelligence() == null ? 10 : p.getIntelligence())
                    .setTalentPoints(p.getTalentPoints() == null ? 0 : p.getTalentPoints())
                    .setPowerScore(p.getPowerScore() == null ? 0 : p.getPowerScore());
            parseTalents(p.getTalentJson()).forEach(b::putTalentLevels);
        }
        return new ProtocolMessage(MessageId.GET_CHARACTER_INFO_SC_RSP, b.build().toByteArray());
    }

    private static ProtocolMessage talentRsp(int retcode, int talentId, int level, int remaining, int power) {
        return new ProtocolMessage(MessageId.ALLOCATE_TALENT_SC_RSP,
                AllocateTalentScRsp.newBuilder()
                        .setRetcode(retcode)
                        .setTalentId(talentId)
                        .setTalentLevel(level)
                        .setRemainingPoints(remaining)
                        .setPowerScore(power == 0 ? 0 : power)
                        .build()
                        .toByteArray());
    }

    private static ProtocolMessage spendRsp(int retcode, long gold) {
        return new ProtocolMessage(MessageId.SPEND_GOLD_SC_RSP,
                SpendGoldScRsp.newBuilder().setRetcode(retcode).setGoldRemaining(gold).build().toByteArray());
    }
}
