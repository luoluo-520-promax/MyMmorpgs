package cn.itcast.demo.mymmorpg.challenge;

import java.util.Collections;
import java.util.List;

/** 挑战关卡 JSON 配置文档（classpath: challenge/challenge_config.json）。 */
public class ChallengeConfigDocument {

    private int id;
    private String name;
    private int challengeType = 1;
    private int waveCount = 1;
    private List<WaveMonster> waves = List.of();
    private List<Integer> starThresholdsSec = List.of(60, 120, 180);
    private String description;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getChallengeType() {
        return challengeType;
    }

    public void setChallengeType(int challengeType) {
        this.challengeType = challengeType;
    }

    public int getWaveCount() {
        return waveCount;
    }

    public void setWaveCount(int waveCount) {
        this.waveCount = waveCount;
    }

    public List<WaveMonster> getWaves() {
        return waves == null ? List.of() : waves;
    }

    public void setWaves(List<WaveMonster> waves) {
        this.waves = waves;
    }

    public List<Integer> getStarThresholdsSec() {
        return starThresholdsSec == null || starThresholdsSec.isEmpty()
                ? List.of(60, 120, 180)
                : starThresholdsSec;
    }

    public void setStarThresholdsSec(List<Integer> starThresholdsSec) {
        this.starThresholdsSec = starThresholdsSec;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public WaveMonster firstWaveOrNull() {
        List<WaveMonster> w = getWaves();
        return w.isEmpty() ? null : w.get(0);
    }

    public static class WaveMonster {
        private int monsterTemplateId;
        private int levelBonus;
        private double hpMul = 1.0;
        private double atkMul = 1.0;

        public int getMonsterTemplateId() {
            return monsterTemplateId;
        }

        public void setMonsterTemplateId(int monsterTemplateId) {
            this.monsterTemplateId = monsterTemplateId;
        }

        public int getLevelBonus() {
            return levelBonus;
        }

        public void setLevelBonus(int levelBonus) {
            this.levelBonus = levelBonus;
        }

        public double getHpMul() {
            return hpMul <= 0 ? 1.0 : hpMul;
        }

        public void setHpMul(double hpMul) {
            this.hpMul = hpMul;
        }

        public double getAtkMul() {
            return atkMul <= 0 ? 1.0 : atkMul;
        }

        public void setAtkMul(double atkMul) {
            this.atkMul = atkMul;
        }
    }

    public static ChallengeConfigDocument fallback(int challengeId) {
        ChallengeConfigDocument doc = new ChallengeConfigDocument();
        doc.setId(challengeId);
        doc.setName("Challenge-" + challengeId);
        doc.setWaveCount(Math.max(1, 1 + (challengeId % 3)));
        WaveMonster wave = new WaveMonster();
        wave.setMonsterTemplateId(0);
        wave.setLevelBonus(challengeId % 20);
        wave.setHpMul(1.0 + (challengeId % 5) * 0.1);
        wave.setAtkMul(1.0);
        doc.setWaves(Collections.singletonList(wave));
        doc.setStarThresholdsSec(List.of(60, 120, 180));
        doc.setDescription("synthetic fallback");
        return doc;
    }
}
