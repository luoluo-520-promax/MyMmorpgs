/**
 * 文件说明
 * 模块：mmorpg-common / 实体
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/entity/Player.java
 * 类型：类
 * 职责：定义 Player，供各业务模块复用与扩展。
 */
package cn.itcast.demo.mymmorpg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "player")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "name", nullable = false, unique = true, length = 64)
    private String name;

    @Column(name = "level", nullable = false)
    private Integer level = 1;

    @Column(name = "vip_right", nullable = false)
    private Integer vipRight = 0;

    @Column(name = "gold", nullable = false)
    private Long gold = 0L;

    @Column(name = "exp", nullable = false)
    private Long exp = 0L;

    @Column(name = "strength", nullable = false)
    private Integer strength = 10;

    @Column(name = "agility", nullable = false)
    private Integer agility = 10;

    @Column(name = "intelligence", nullable = false)
    private Integer intelligence = 10;

    @Column(name = "talent_points", nullable = false)
    private Integer talentPoints = 0;

    /** JSON map: talentId -> level，如 {"1":2,"2":1} */
    @Column(name = "talent_json", length = 512)
    private String talentJson = "{}";

    @Column(name = "power_score", nullable = false)
    private Integer powerScore = 0;

    /** 当前穿戴皮肤 ID；0 表示默认皮 */
    @Column(name = "equipped_skin_id", nullable = false)
    private Integer equippedSkinId = 0;

    /** 封角色：仅禁止该角色进入游戏，同账号其他角色不受影响 */
    @Column(name = "banned", nullable = false)
    private Boolean banned = false;

    @Column(name = "ban_reason", length = 255)
    private String banReason;

    @Column(name = "ban_until")
    private java.time.LocalDateTime banUntil;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Integer getVipRight() {
        return vipRight;
    }

    public void setVipRight(Integer vipRight) {
        this.vipRight = vipRight;
    }

    public Long getGold() {
        return gold;
    }

    public void setGold(Long gold) {
        this.gold = gold;
    }

    public Long getExp() {
        return exp;
    }

    public void setExp(Long exp) {
        this.exp = exp;
    }

    public Integer getStrength() {
        return strength;
    }

    public void setStrength(Integer strength) {
        this.strength = strength;
    }

    public Integer getAgility() {
        return agility;
    }

    public void setAgility(Integer agility) {
        this.agility = agility;
    }

    public Integer getIntelligence() {
        return intelligence;
    }

    public void setIntelligence(Integer intelligence) {
        this.intelligence = intelligence;
    }

    public Integer getTalentPoints() {
        return talentPoints;
    }

    public void setTalentPoints(Integer talentPoints) {
        this.talentPoints = talentPoints;
    }

    public String getTalentJson() {
        return talentJson;
    }

    public void setTalentJson(String talentJson) {
        this.talentJson = talentJson;
    }

    public Integer getPowerScore() {
        return powerScore;
    }

    public void setPowerScore(Integer powerScore) {
        this.powerScore = powerScore;
    }

    public Integer getEquippedSkinId() {
        return equippedSkinId;
    }

    public void setEquippedSkinId(Integer equippedSkinId) {
        this.equippedSkinId = equippedSkinId == null ? 0 : equippedSkinId;
    }

    public Boolean getBanned() {
        return banned;
    }

    public void setBanned(Boolean banned) {
        this.banned = banned;
    }

    public String getBanReason() {
        return banReason;
    }

    public void setBanReason(String banReason) {
        this.banReason = banReason;
    }

    public java.time.LocalDateTime getBanUntil() {
        return banUntil;
    }

    public void setBanUntil(java.time.LocalDateTime banUntil) {
        this.banUntil = banUntil;
    }

    /** 战力粗算：等级*10 + 三维属性 + 天赋点已用加成 */
    public int recalcPowerScore() {
        int s = strength == null ? 0 : strength;
        int a = agility == null ? 0 : agility;
        int i = intelligence == null ? 0 : intelligence;
        int lv = level == null ? 1 : level;
        int score = lv * 10 + s + a + i;
        this.powerScore = score;
        return score;
    }
}
