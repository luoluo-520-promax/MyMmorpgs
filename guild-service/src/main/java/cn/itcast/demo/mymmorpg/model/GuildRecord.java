package cn.itcast.demo.mymmorpg.model;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 内存态公会（schema 对齐 guild 表；持久化可后续接 JPA）。 */
public class GuildRecord {

    private final long id;
    private String name;
    private int level = 1;
    private long exp;
    private long fund;
    private String notice = "";
    private long leaderId;
    private final long createTimeMs;
    private final ConcurrentHashMap<Long, GuildMemberRecord> members = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> techLevels = new ConcurrentHashMap<>();

    public GuildRecord(long id, String name, long leaderId) {
        this.id = id;
        this.name = name;
        this.leaderId = leaderId;
        this.createTimeMs = System.currentTimeMillis();
        techLevels.put("ATTACK", 0);
        techLevels.put("HP", 0);
        techLevels.put("STAMINA", 0);
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(3, level));
    }

    public long getExp() {
        return exp;
    }

    public void setExp(long exp) {
        this.exp = Math.max(0L, exp);
    }

    public long getFund() {
        return fund;
    }

    public void setFund(long fund) {
        this.fund = Math.max(0L, fund);
    }

    public String getNotice() {
        return notice;
    }

    public void setNotice(String notice) {
        this.notice = notice == null ? "" : notice;
    }

    public long getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(long leaderId) {
        this.leaderId = leaderId;
    }

    public long getCreateTimeMs() {
        return createTimeMs;
    }

    public ConcurrentHashMap<Long, GuildMemberRecord> getMembers() {
        return members;
    }

    public ConcurrentHashMap<String, Integer> getTechLevels() {
        return techLevels;
    }

    public int memberCapacity() {
        return switch (level) {
            case 1 -> 20;
            case 2 -> 30;
            default -> 40;
        };
    }

    /** finalAttr = base * (1 + attack%)；体力为固定加成。 */
    public double attackBonusRatio() {
        return techLevels.getOrDefault("ATTACK", 0) * 0.02;
    }

    public double hpBonusRatio() {
        return techLevels.getOrDefault("HP", 0) * 0.02;
    }

    public int staminaBonusFlat() {
        return techLevels.getOrDefault("STAMINA", 0) * 5;
    }

    public static final class GuildMemberRecord {
        private final long playerId;
        private GuildRole role;
        private final AtomicLong contribution = new AtomicLong();
        private final long joinTimeMs;
        private volatile long lastLoginMs;

        public GuildMemberRecord(long playerId, GuildRole role) {
            this.playerId = playerId;
            this.role = role;
            this.joinTimeMs = System.currentTimeMillis();
            this.lastLoginMs = this.joinTimeMs;
        }

        public long getPlayerId() {
            return playerId;
        }

        public GuildRole getRole() {
            return role;
        }

        public void setRole(GuildRole role) {
            this.role = role;
        }

        public AtomicLong getContribution() {
            return contribution;
        }

        public long getJoinTimeMs() {
            return joinTimeMs;
        }

        public long getLastLoginMs() {
            return lastLoginMs;
        }

        public void setLastLoginMs(long lastLoginMs) {
            this.lastLoginMs = lastLoginMs;
        }
    }
}
