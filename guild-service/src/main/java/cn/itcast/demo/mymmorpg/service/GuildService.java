package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.GuildBagClient;
import cn.itcast.demo.mymmorpg.client.GuildChatClient;
import cn.itcast.demo.mymmorpg.model.GuildRecord;
import cn.itcast.demo.mymmorpg.model.GuildRecord.GuildMemberRecord;
import cn.itcast.demo.mymmorpg.model.GuildRole;
import cn.itcast.demo.mymmorpg.protocol.BagRetCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 公会组织：创建/加入/退出/转让/科技；人数上限随等级 20/30/40。
 */
@Service
public class GuildService {

    private static final Logger log = LoggerFactory.getLogger(GuildService.class);

    public static final int ITEM_MORA = 10001;
    public static final int ITEM_PRIMOGEM = 10002;
    public static final int CREATE_MORA_COST = 30_000;
    public static final int CREATE_PRIMOGEM_COST = 300;
    public static final long LEADER_INACTIVE_MS = 7L * 24 * 3600 * 1000;
    public static final String REDIS_LAST_ACTIVE = "guild:last_active:";

    private final ConcurrentHashMap<Long, GuildRecord> guilds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> playerGuildIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> nameIndex = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1000);

    private final ObjectProvider<GuildBagClient> bagClient;
    private final ObjectProvider<GuildChatClient> chatClient;
    private final ObjectProvider<StringRedisTemplate> redisTemplate;

    public GuildService(ObjectProvider<GuildBagClient> bagClient,
                        ObjectProvider<GuildChatClient> chatClient,
                        ObjectProvider<StringRedisTemplate> redisTemplate) {
        this.bagClient = bagClient;
        this.chatClient = chatClient;
        this.redisTemplate = redisTemplate;
    }

    public Map<String, Object> create(long playerId, String name) {
        if (playerId <= 0) {
            return err("INVALID_PLAYER");
        }
        if (name == null || name.isBlank()) {
            return err("INVALID_NAME");
        }
        String trimmed = name.trim();
        if (playerGuildIndex.containsKey(playerId)) {
            return err("ALREADY_IN_GUILD");
        }
        if (nameIndex.containsKey(trimmed)) {
            return err("NAME_TAKEN");
        }
        int consumeRc = consumeCreateCost(playerId);
        if (consumeRc == BagRetCode.COUNT_NOT_ENOUGH) {
            return err("ERR_INSUFFICIENT");
        }
        if (consumeRc != BagRetCode.OK) {
            return err("BAG_ERROR", Map.of("bagRetcode", consumeRc));
        }
        long guildId = idSeq.incrementAndGet();
        GuildRecord guild = new GuildRecord(guildId, trimmed, playerId);
        GuildMemberRecord leader = new GuildMemberRecord(playerId, GuildRole.LEADER);
        guild.getMembers().put(playerId, leader);
        guilds.put(guildId, guild);
        playerGuildIndex.put(playerId, guildId);
        nameIndex.put(trimmed, guildId);
        touchActive(guildId);
        notifyChatCreated(guildId);
        syncChatMember(playerId, guildId, false);
        return ok(toView(guild));
    }

    public Map<String, Object> join(long playerId, long guildId) {
        GuildRecord guild = guilds.get(guildId);
        if (guild == null) {
            return err("GUILD_NOT_FOUND");
        }
        if (playerGuildIndex.containsKey(playerId)) {
            return err("ALREADY_IN_GUILD");
        }
        if (guild.getMembers().size() >= guild.memberCapacity()) {
            return err("GUILD_FULL");
        }
        guild.getMembers().put(playerId, new GuildMemberRecord(playerId, GuildRole.MEMBER));
        playerGuildIndex.put(playerId, guildId);
        touchActive(guildId);
        syncChatMember(playerId, guildId, false);
        return ok(toView(guild));
    }

    public Map<String, Object> leave(long playerId) {
        Long guildId = playerGuildIndex.get(playerId);
        if (guildId == null) {
            return err("NOT_IN_GUILD");
        }
        GuildRecord guild = guilds.get(guildId);
        if (guild == null) {
            playerGuildIndex.remove(playerId);
            return err("GUILD_NOT_FOUND");
        }
        GuildMemberRecord self = guild.getMembers().get(playerId);
        if (self != null && self.getRole() == GuildRole.LEADER && guild.getMembers().size() > 1) {
            return err("LEADER_MUST_TRANSFER");
        }
        guild.getMembers().remove(playerId);
        playerGuildIndex.remove(playerId);
        syncChatMember(playerId, guildId, true);
        if (guild.getMembers().isEmpty()) {
            dissolve(guild);
        }
        return Map.of("ok", true);
    }

    public Map<String, Object> transfer(long leaderId, long targetPlayerId, boolean requireOnline) {
        Long guildId = playerGuildIndex.get(leaderId);
        if (guildId == null) {
            return err("NOT_IN_GUILD");
        }
        GuildRecord guild = guilds.get(guildId);
        if (guild == null) {
            return err("GUILD_NOT_FOUND");
        }
        GuildMemberRecord leader = guild.getMembers().get(leaderId);
        if (leader == null || leader.getRole() != GuildRole.LEADER) {
            return err("NOT_LEADER");
        }
        GuildMemberRecord target = guild.getMembers().get(targetPlayerId);
        if (target == null) {
            return err("TARGET_NOT_MEMBER");
        }
        if (requireOnline && !isMemberRecentlyActive(target, System.currentTimeMillis())) {
            return err("TARGET_OFFLINE");
        }
        leader.setRole(GuildRole.MEMBER);
        target.setRole(GuildRole.LEADER);
        guild.setLeaderId(targetPlayerId);
        touchActive(guildId);
        return ok(toView(guild));
    }

    public Map<String, Object> upgradeTech(long playerId, String techLine) {
        Long guildId = playerGuildIndex.get(playerId);
        if (guildId == null) {
            return err("NOT_IN_GUILD");
        }
        GuildRecord guild = guilds.get(guildId);
        if (guild == null) {
            return err("GUILD_NOT_FOUND");
        }
        GuildMemberRecord m = guild.getMembers().get(playerId);
        if (m == null || (m.getRole() != GuildRole.LEADER && m.getRole() != GuildRole.VICE)) {
            return err("NO_PERMISSION");
        }
        String line = techLine == null ? "" : techLine.trim().toUpperCase();
        if (!guild.getTechLevels().containsKey(line)) {
            return err("INVALID_TECH");
        }
        int lv = guild.getTechLevels().get(line);
        if (lv >= 3) {
            return err("TECH_MAX");
        }
        guild.getTechLevels().put(line, lv + 1);
        return ok(toView(guild));
    }

    public Map<String, Object> info(long guildId) {
        GuildRecord guild = guilds.get(guildId);
        if (guild == null) {
            return err("GUILD_NOT_FOUND");
        }
        return ok(toView(guild));
    }

    public Map<String, Object> myGuild(long playerId) {
        Long guildId = playerGuildIndex.get(playerId);
        if (guildId == null) {
            return err("NOT_IN_GUILD");
        }
        return info(guildId);
    }

    public void touchPlayerLogin(long playerId) {
        Long guildId = playerGuildIndex.get(playerId);
        if (guildId == null) {
            return;
        }
        GuildRecord guild = guilds.get(guildId);
        if (guild == null) {
            return;
        }
        GuildMemberRecord m = guild.getMembers().get(playerId);
        if (m != null) {
            m.setLastLoginMs(System.currentTimeMillis());
        }
        touchActive(guildId);
    }

    /** 每日 4:00：会长 7 天未登录则转让给贡献最高副会/成员。 */
    public List<Map<String, Object>> scanLeaderTransfer() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> transferred = new ArrayList<>();
        for (GuildRecord guild : guilds.values()) {
            GuildMemberRecord leader = guild.getMembers().get(guild.getLeaderId());
            if (leader == null) {
                continue;
            }
            if (now - leader.getLastLoginMs() < LEADER_INACTIVE_MS) {
                continue;
            }
            GuildMemberRecord next = guild.getMembers().values().stream()
                    .filter(m -> m.getPlayerId() != leader.getPlayerId())
                    .sorted(Comparator
                            .comparingInt((GuildMemberRecord m) -> m.getRole() == GuildRole.VICE ? 0 : 1)
                            .thenComparingLong(m -> -m.getContribution().get()))
                    .findFirst()
                    .orElse(null);
            if (next == null) {
                continue;
            }
            leader.setRole(GuildRole.MEMBER);
            next.setRole(GuildRole.LEADER);
            guild.setLeaderId(next.getPlayerId());
            touchActive(guild.getId());
            transferred.add(Map.of(
                    "guildId", guild.getId(),
                    "from", leader.getPlayerId(),
                    "to", next.getPlayerId()));
        }
        return transferred;
    }

    public GuildRecord requireGuild(long guildId) {
        GuildRecord g = guilds.get(guildId);
        if (g == null) {
            throw new IllegalArgumentException("GUILD_NOT_FOUND");
        }
        return g;
    }

    public Long guildIdOf(long playerId) {
        return playerGuildIndex.get(playerId);
    }

    public void addContribution(long guildId, long playerId, long delta) {
        GuildRecord guild = guilds.get(guildId);
        if (guild == null) {
            return;
        }
        GuildMemberRecord m = guild.getMembers().get(playerId);
        if (m != null && delta > 0) {
            m.getContribution().addAndGet(delta);
        }
    }

    Map<String, Object> toView(GuildRecord guild) {
        List<Map<String, Object>> members = new ArrayList<>();
        for (GuildMemberRecord m : guild.getMembers().values()) {
            members.add(Map.of(
                    "playerId", m.getPlayerId(),
                    "role", m.getRole().name(),
                    "contribution", m.getContribution().get(),
                    "joinTimeMs", m.getJoinTimeMs()));
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("guildId", guild.getId());
        view.put("name", guild.getName());
        view.put("level", guild.getLevel());
        view.put("exp", guild.getExp());
        view.put("fund", guild.getFund());
        view.put("notice", guild.getNotice());
        view.put("leaderId", guild.getLeaderId());
        view.put("capacity", guild.memberCapacity());
        view.put("memberCount", guild.getMembers().size());
        view.put("tech", Map.copyOf(guild.getTechLevels()));
        view.put("attackBonusRatio", guild.attackBonusRatio());
        view.put("hpBonusRatio", guild.hpBonusRatio());
        view.put("staminaBonusFlat", guild.staminaBonusFlat());
        view.put("members", members);
        return view;
    }

    private void dissolve(GuildRecord guild) {
        nameIndex.remove(guild.getName());
        guilds.remove(guild.getId());
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (redis != null) {
            redis.delete(REDIS_LAST_ACTIVE + guild.getId());
        }
    }

    private void touchActive(long guildId) {
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (redis != null) {
            redis.opsForValue().set(REDIS_LAST_ACTIVE + guildId, Long.toString(System.currentTimeMillis()));
        }
    }

    private int consumeCreateCost(long playerId) {
        GuildBagClient bag = bagClient.getIfAvailable();
        if (bag == null) {
            // 单测/无 bag 时视为扣费成功
            return BagRetCode.OK;
        }
        try {
            Map<String, Object> body = Map.of("costs", List.of(
                    Map.of("itemId", ITEM_MORA, "count", CREATE_MORA_COST),
                    Map.of("itemId", ITEM_PRIMOGEM, "count", CREATE_PRIMOGEM_COST)));
            Integer rc = bag.consume(playerId, body);
            return rc == null ? BagRetCode.ITEM_UNAVAILABLE : rc;
        } catch (Exception e) {
            log.warn("guild create consume failed playerId={}", playerId, e);
            return BagRetCode.ITEM_UNAVAILABLE;
        }
    }

    private void notifyChatCreated(long guildId) {
        GuildChatClient chat = chatClient.getIfAvailable();
        if (chat == null) {
            return;
        }
        try {
            chat.onCreated(Map.of("guildId", Long.toString(guildId)));
        } catch (Exception e) {
            log.warn("chat guild created notify failed guildId={}", guildId, e);
        }
    }

    private void syncChatMember(long playerId, long guildId, boolean leave) {
        GuildChatClient chat = chatClient.getIfAvailable();
        if (chat == null) {
            return;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("playerId", playerId);
            body.put("guildId", Long.toString(guildId));
            body.put("leave", leave);
            chat.syncMember(body);
        } catch (Exception e) {
            log.warn("chat guild member sync failed playerId={} guildId={}", playerId, guildId, e);
        }
    }

    private static boolean isMemberRecentlyActive(GuildMemberRecord m, long now) {
        return now - m.getLastLoginMs() < 24L * 3600 * 1000;
    }

    private static Map<String, Object> ok(Map<String, Object> data) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.putAll(data);
        return out;
    }

    private static Map<String, Object> err(String code) {
        return Map.of("ok", false, "error", code);
    }

    private static Map<String, Object> err(String code, Map<String, Object> extra) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", false);
        out.put("error", code);
        out.putAll(extra);
        return out;
    }
}
