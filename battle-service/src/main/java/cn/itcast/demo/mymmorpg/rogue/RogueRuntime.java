package cn.itcast.demo.mymmorpg.rogue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 玩家当前肉鸽局运行时。 */
public class RogueRuntime {

    private final long playerId;
    private final int rogueId;
    private final int difficulty;
    private int floor = 1;
    private int wave = 1;
    private int roomId = 1;
    private int currency = 100;
    private int talentPoints;
    private final List<Integer> blessings = new ArrayList<>();
    private final Map<Integer, Integer> talents = new HashMap<>();
    private volatile long battleId;

    public RogueRuntime(long playerId, int rogueId, int difficulty) {
        this.playerId = playerId;
        this.rogueId = rogueId;
        this.difficulty = Math.max(1, difficulty);
    }

    public long getPlayerId() {
        return playerId;
    }

    public int getRogueId() {
        return rogueId;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public int getFloor() {
        return floor;
    }

    public int getWave() {
        return wave;
    }

    public int getRoomId() {
        return roomId;
    }

    public int getCurrency() {
        return currency;
    }

    public int getTalentPoints() {
        return talentPoints;
    }

    public long getBattleId() {
        return battleId;
    }

    public void setBattleId(long battleId) {
        this.battleId = battleId;
    }

    public void clearBattleId() {
        this.battleId = 0L;
    }

    public List<Integer> getBlessings() {
        return List.copyOf(blessings);
    }

    public Map<Integer, Integer> getTalents() {
        return Map.copyOf(talents);
    }

    public int getTalentLevel(int talentId) {
        return talents.getOrDefault(talentId, 0);
    }

    public void moveTo(int targetRoomId) {
        this.roomId = targetRoomId;
        this.wave++;
        if (wave > 3) {
            wave = 1;
            floor++;
        }
        currency += 10 + difficulty * 5;
        this.battleId = 0L;
    }

    public boolean addBlessing(int blessingId) {
        if (blessingId <= 0 || blessings.contains(blessingId)) {
            return false;
        }
        blessings.add(blessingId);
        return true;
    }

    /**
     * 消耗局内天赋点加点。
     *
     * @return false 表示参数非法或点数不足
     */
    public boolean allocateTalent(int talentId, int points) {
        if (talentId <= 0 || points <= 0 || talentPoints < points) {
            return false;
        }
        talentPoints -= points;
        talents.merge(talentId, points, Integer::sum);
        return true;
    }

    /**
     * 战斗结算回写：胜加货币与天赋点；败略扣货币；均清除 battleId。
     *
     * @param result 0 败 1 胜 2 平局/逃跑
     */
    public void applyBattleResult(int result) {
        if (result == 1) {
            currency += 20 + difficulty * 10;
            talentPoints += 1;
            int autoBlessing = 1000 + floor * 10 + wave;
            if (!blessings.contains(autoBlessing)) {
                blessings.add(autoBlessing);
            }
        } else if (result == 0) {
            currency = Math.max(0, currency - 5);
        }
        this.battleId = 0L;
    }

    /** Redis 反序列化恢复运行时状态。 */
    void restoreState(int floor, int wave, int roomId, int currency, long battleId,
                      List<Integer> blessingIds, int talentPoints, Map<Integer, Integer> talentLevels) {
        this.floor = Math.max(1, floor);
        this.wave = Math.max(1, wave);
        this.roomId = Math.max(1, roomId);
        this.currency = Math.max(0, currency);
        this.battleId = battleId;
        this.talentPoints = Math.max(0, talentPoints);
        this.blessings.clear();
        if (blessingIds != null) {
            for (Integer id : blessingIds) {
                if (id != null && id > 0 && !this.blessings.contains(id)) {
                    this.blessings.add(id);
                }
            }
        }
        this.talents.clear();
        if (talentLevels != null) {
            for (Map.Entry<Integer, Integer> e : talentLevels.entrySet()) {
                if (e.getKey() != null && e.getKey() > 0 && e.getValue() != null && e.getValue() > 0) {
                    this.talents.put(e.getKey(), e.getValue());
                }
            }
        }
    }
}
