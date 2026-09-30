package cn.itcast.demo.mymmorpg.world.social;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 区域世界频道：跨服广播稀有精英击杀，并附带临时加入战局入口。
 */
@Service
public class RegionWorldChannelService {

    private final AtomicLong seq = new AtomicLong(1);
    /** regionId → 在线玩家（可跨服） */
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, String>> onlineByRegion =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Map<String, Object>>> recentBroadcasts =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> joinTokens = new ConcurrentHashMap<>();

    public Map<String, Object> joinChannel(String regionId, long playerId, String serverId) {
        onlineByRegion.computeIfAbsent(regionId, id -> new ConcurrentHashMap<>())
                .put(playerId, serverId == null ? "local" : serverId);
        return Map.of("ok", true, "regionId", regionId, "playerId", playerId,
                "online", onlineByRegion.get(regionId).size());
    }

    public Map<String, Object> leaveChannel(String regionId, long playerId) {
        ConcurrentHashMap<Long, String> online = onlineByRegion.get(regionId);
        if (online != null) {
            online.remove(playerId);
        }
        return Map.of("ok", true);
    }

    /**
     * 击杀稀有精英后向区域频道广播，并生成 JoinBattle token。
     */
    public Map<String, Object> broadcastEliteKill(
            String regionId, long killerPlayerId, String eliteName, long nowMs) {
        long battleId = seq.getAndIncrement();
        String token = "join:" + regionId + ":" + battleId;
        joinTokens.put(battleId, token);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "ELITE_KILL");
        event.put("regionId", regionId);
        event.put("killerPlayerId", killerPlayerId);
        event.put("eliteName", eliteName);
        event.put("message", killerPlayerId + " 击杀了 " + eliteName + "！");
        event.put("battleId", battleId);
        event.put("joinBattleToken", token);
        event.put("joinBattleButton", true);
        event.put("atMs", nowMs);
        List<Long> recipients = new ArrayList<>(
                onlineByRegion.getOrDefault(regionId, new ConcurrentHashMap<>()).keySet());
        event.put("recipientCount", recipients.size());
        event.put("recipients", recipients);
        pushBroadcast(regionId, event);
        Map<String, Object> body = new LinkedHashMap<>(event);
        body.put("ok", true);
        return body;
    }

    /**
     * 世界 Boss 结算 MVP + 最高伤害数字，强化社交炫耀。
     */
    public Map<String, Object> broadcastBossMvp(
            String regionId, long mvpPlayerId, long topDamage, String bossName, long nowMs) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "BOSS_MVP");
        event.put("regionId", regionId);
        event.put("mvpPlayerId", mvpPlayerId);
        event.put("topDamage", topDamage);
        event.put("bossName", bossName == null ? "WorldBoss" : bossName);
        event.put("message", "MVP " + mvpPlayerId + " 最高伤害 " + topDamage
                + (bossName == null ? "" : " @" + bossName));
        event.put("atMs", nowMs);
        List<Long> recipients = new ArrayList<>(
                onlineByRegion.getOrDefault(regionId, new ConcurrentHashMap<>()).keySet());
        event.put("recipientCount", recipients.size());
        event.put("recipients", recipients);
        pushBroadcast(regionId, event);
        Map<String, Object> body = new LinkedHashMap<>(event);
        body.put("ok", true);
        return body;
    }

    private void pushBroadcast(String regionId, Map<String, Object> event) {
        recentBroadcasts.computeIfAbsent(regionId, id -> new ArrayList<>());
        synchronized (recentBroadcasts.get(regionId)) {
            List<Map<String, Object>> list = recentBroadcasts.get(regionId);
            list.add(event);
            if (list.size() > 50) {
                list.remove(0);
            }
        }
    }

    /** 非组队玩家凭 token 临时加入，奖励独立结算。 */
    public Map<String, Object> joinBattle(long playerId, long battleId, String token) {
        String expect = joinTokens.get(battleId);
        if (expect == null || !expect.equals(token)) {
            return Map.of("ok", false, "error", "invalid_token");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("battleId", battleId);
        body.put("joined", true);
        body.put("rewardMode", "INDEPENDENT");
        body.put("passerbyGrantEligible", true);
        body.put("hint", "临时蹭奖励，不共享队伍伤害归属");
        return body;
    }

    /**
     * 世界 Boss 开战时注册协战贡献池。
     */
    public Map<String, Object> openBossBattle(String regionId, String bossName, long maxHp, long nowMs) {
        long battleId = seq.getAndIncrement();
        String token = "join:" + regionId + ":" + battleId;
        joinTokens.put(battleId, token);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "BOSS_BATTLE_OPEN");
        event.put("regionId", regionId);
        event.put("bossName", bossName);
        event.put("battleId", battleId);
        event.put("maxHp", maxHp);
        event.put("joinBattleToken", token);
        event.put("fightContributionPool", true);
        event.put("atMs", nowMs);
        pushBroadcast(regionId, event);
        Map<String, Object> body = new LinkedHashMap<>(event);
        body.put("ok", true);
        return body;
    }

    public List<Map<String, Object>> recent(String regionId) {
        List<Map<String, Object>> list = recentBroadcasts.get(regionId);
        return list == null ? List.of() : List.copyOf(list);
    }
}
