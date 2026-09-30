package cn.itcast.demo.mymmorpg.model.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 供 AI 顾问使用的当前玩家画像（脱敏后的可解释上下文）。
 */
public class PlayerAiProfile {

    private long playerId;
    private String name;
    private int level;
    private long exp;
    private long gold;
    private int vipRight;
    private int strength;
    private int agility;
    private int intelligence;
    private int talentPoints;
    private String talentJson;
    private int powerScore;
    private List<Integer> learnedSkillIds = new ArrayList<>();
    private List<BagItemLite> bagItems = new ArrayList<>();
    private PlayerBattleLite personalBattle = new PlayerBattleLite();
    private Integer powerRankHint;
    private Integer levelRankHint;

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
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
        this.level = level;
    }

    public long getExp() {
        return exp;
    }

    public void setExp(long exp) {
        this.exp = exp;
    }

    public long getGold() {
        return gold;
    }

    public void setGold(long gold) {
        this.gold = gold;
    }

    public int getVipRight() {
        return vipRight;
    }

    public void setVipRight(int vipRight) {
        this.vipRight = vipRight;
    }

    public int getStrength() {
        return strength;
    }

    public void setStrength(int strength) {
        this.strength = strength;
    }

    public int getAgility() {
        return agility;
    }

    public void setAgility(int agility) {
        this.agility = agility;
    }

    public int getIntelligence() {
        return intelligence;
    }

    public void setIntelligence(int intelligence) {
        this.intelligence = intelligence;
    }

    public int getTalentPoints() {
        return talentPoints;
    }

    public void setTalentPoints(int talentPoints) {
        this.talentPoints = talentPoints;
    }

    public String getTalentJson() {
        return talentJson;
    }

    public void setTalentJson(String talentJson) {
        this.talentJson = talentJson;
    }

    public int getPowerScore() {
        return powerScore;
    }

    public void setPowerScore(int powerScore) {
        this.powerScore = powerScore;
    }

    public List<Integer> getLearnedSkillIds() {
        return learnedSkillIds;
    }

    public void setLearnedSkillIds(List<Integer> learnedSkillIds) {
        this.learnedSkillIds = learnedSkillIds;
    }

    public List<BagItemLite> getBagItems() {
        return bagItems;
    }

    public void setBagItems(List<BagItemLite> bagItems) {
        this.bagItems = bagItems;
    }

    public PlayerBattleLite getPersonalBattle() {
        return personalBattle;
    }

    public void setPersonalBattle(PlayerBattleLite personalBattle) {
        this.personalBattle = personalBattle;
    }

    public Integer getPowerRankHint() {
        return powerRankHint;
    }

    public void setPowerRankHint(Integer powerRankHint) {
        this.powerRankHint = powerRankHint;
    }

    public Integer getLevelRankHint() {
        return levelRankHint;
    }

    public void setLevelRankHint(Integer levelRankHint) {
        this.levelRankHint = levelRankHint;
    }

    public static class BagItemLite {
        private int itemConfigId;
        private int count;
        private int equipSlot;

        public BagItemLite() {
        }

        public BagItemLite(int itemConfigId, int count, int equipSlot) {
            this.itemConfigId = itemConfigId;
            this.count = count;
            this.equipSlot = equipSlot;
        }

        public int getItemConfigId() {
            return itemConfigId;
        }

        public void setItemConfigId(int itemConfigId) {
            this.itemConfigId = itemConfigId;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public int getEquipSlot() {
            return equipSlot;
        }

        public void setEquipSlot(int equipSlot) {
            this.equipSlot = equipSlot;
        }
    }
}
