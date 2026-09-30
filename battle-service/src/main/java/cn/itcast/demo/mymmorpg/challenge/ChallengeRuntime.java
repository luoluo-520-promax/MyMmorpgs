package cn.itcast.demo.mymmorpg.challenge;

import java.util.List;

/**
 * 单次挑战关卡内存状态（对齐 MyLunarCore ChallengeRuntime，精简版）。
 */
public class ChallengeRuntime {

    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_VICTORY = 2;
    public static final int STATUS_DEFEAT = 3;

    private final long challengeUid;
    private final long playerId;
    private final int challengeType;
    private final int challengeId;
    private final int waveCount;
    private final List<Integer> starThresholdsSec;
    private final long startTimeMillis;
    private volatile long battleId;
    private volatile int status = STATUS_RUNNING;
    private volatile int score;
    private volatile int stars;

    public ChallengeRuntime(long challengeUid, long playerId, int challengeType, int challengeId, int waveCount) {
        this(challengeUid, playerId, challengeType, challengeId, waveCount, List.of(60, 120, 180));
    }

    public ChallengeRuntime(
            long challengeUid, long playerId, int challengeType, int challengeId, int waveCount,
            List<Integer> starThresholdsSec) {
        this.challengeUid = challengeUid;
        this.playerId = playerId;
        this.challengeType = challengeType;
        this.challengeId = challengeId;
        this.waveCount = waveCount;
        this.starThresholdsSec = starThresholdsSec == null || starThresholdsSec.isEmpty()
                ? List.of(60, 120, 180)
                : List.copyOf(starThresholdsSec);
        this.startTimeMillis = System.currentTimeMillis();
    }

    public long getChallengeUid() {
        return challengeUid;
    }

    public long getPlayerId() {
        return playerId;
    }

    public int getChallengeType() {
        return challengeType;
    }

    public int getChallengeId() {
        return challengeId;
    }

    public int getWaveCount() {
        return waveCount;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public long getBattleId() {
        return battleId;
    }

    public void setBattleId(long battleId) {
        this.battleId = battleId;
    }

    public int getStatus() {
        return status;
    }

    public int getScore() {
        return score;
    }

    public int getStars() {
        return stars;
    }

    public void finish(boolean victory) {
        this.status = victory ? STATUS_VICTORY : STATUS_DEFEAT;
        long elapsedSec = Math.max(1L, (System.currentTimeMillis() - startTimeMillis) / 1000L);
        this.score = victory ? (int) Math.max(100, 1000 - elapsedSec * 10) : 0;
        if (!victory) {
            this.stars = 0;
            return;
        }
        int s3 = thresholdAt(0, 60);
        int s2 = thresholdAt(1, 120);
        int s1 = thresholdAt(2, 180);
        if (elapsedSec < s3) {
            this.stars = 3;
        } else if (elapsedSec < s2) {
            this.stars = 2;
        } else if (elapsedSec < s1) {
            this.stars = 1;
        } else {
            this.stars = 1;
        }
    }

    private int thresholdAt(int index, int defaultSec) {
        if (index < starThresholdsSec.size()) {
            Integer v = starThresholdsSec.get(index);
            return v == null || v <= 0 ? defaultSec : v;
        }
        return defaultSec;
    }
}
