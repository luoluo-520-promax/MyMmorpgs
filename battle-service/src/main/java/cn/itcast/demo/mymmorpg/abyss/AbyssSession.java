package cn.itcast.demo.mymmorpg.abyss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Redis {@code abyss:session:{playerId}} 序列化体：连打不重置血量/能量/CD。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AbyssSession {

    private long playerId;
    private int floorIndex = 1;
    private int chamberIndex = 1;
    private int hp = 10000;
    private int energy;
    private List<Integer> skillCdMs = new ArrayList<>();
    private long chamberStartedAtMs;
    private int starsEarned;
    private List<Integer> claimedMilestones = new ArrayList<>();
    private Map<String, Object> blessing = new HashMap<>();

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public int getFloorIndex() {
        return floorIndex;
    }

    public void setFloorIndex(int floorIndex) {
        this.floorIndex = floorIndex;
    }

    public int getChamberIndex() {
        return chamberIndex;
    }

    public void setChamberIndex(int chamberIndex) {
        this.chamberIndex = chamberIndex;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = hp;
    }

    public int getEnergy() {
        return energy;
    }

    public void setEnergy(int energy) {
        this.energy = energy;
    }

    public List<Integer> getSkillCdMs() {
        return skillCdMs;
    }

    public void setSkillCdMs(List<Integer> skillCdMs) {
        this.skillCdMs = skillCdMs == null ? new ArrayList<>() : skillCdMs;
    }

    public long getChamberStartedAtMs() {
        return chamberStartedAtMs;
    }

    public void setChamberStartedAtMs(long chamberStartedAtMs) {
        this.chamberStartedAtMs = chamberStartedAtMs;
    }

    public int getStarsEarned() {
        return starsEarned;
    }

    public void setStarsEarned(int starsEarned) {
        this.starsEarned = starsEarned;
    }

    public List<Integer> getClaimedMilestones() {
        return claimedMilestones;
    }

    public void setClaimedMilestones(List<Integer> claimedMilestones) {
        this.claimedMilestones = claimedMilestones == null ? new ArrayList<>() : claimedMilestones;
    }

    public Map<String, Object> getBlessing() {
        return blessing;
    }

    public void setBlessing(Map<String, Object> blessing) {
        this.blessing = blessing == null ? new HashMap<>() : blessing;
    }
}
