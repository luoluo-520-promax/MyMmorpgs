package cn.itcast.demo.mymmorpg.abyss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** abyss_floor_config.json 根文档：3 层 × 4 间。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AbyssFloorConfigDocument {

    private String seasonId = "default";
    private Blessing blessing = new Blessing();
    private List<Floor> floors = new ArrayList<>();

    public String getSeasonId() {
        return seasonId;
    }

    public void setSeasonId(String seasonId) {
        this.seasonId = seasonId;
    }

    public Blessing getBlessing() {
        return blessing;
    }

    public void setBlessing(Blessing blessing) {
        this.blessing = blessing;
    }

    public List<Floor> getFloors() {
        return floors;
    }

    public void setFloors(List<Floor> floors) {
        this.floors = floors;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Blessing {
        private String type = "CHARGE_ATTACK";
        private double bonus = 0.3;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public double getBonus() {
            return bonus;
        }

        public void setBonus(double bonus) {
            this.bonus = bonus;
        }

        public Map<String, Object> toMap() {
            return Map.of("type", type, "bonus", bonus);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Floor {
        private int floorIndex;
        private List<Chamber> chambers = new ArrayList<>();

        public int getFloorIndex() {
            return floorIndex;
        }

        public void setFloorIndex(int floorIndex) {
            this.floorIndex = floorIndex;
        }

        public List<Chamber> getChambers() {
            return chambers;
        }

        public void setChambers(List<Chamber> chambers) {
            this.chambers = chambers;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Chamber {
        private int chamberIndex;
        private String monsterGroup = "default";
        private int timeLimitSec = 180;

        public int getChamberIndex() {
            return chamberIndex;
        }

        public void setChamberIndex(int chamberIndex) {
            this.chamberIndex = chamberIndex;
        }

        public String getMonsterGroup() {
            return monsterGroup;
        }

        public void setMonsterGroup(String monsterGroup) {
            this.monsterGroup = monsterGroup;
        }

        public int getTimeLimitSec() {
            return timeLimitSec;
        }

        public void setTimeLimitSec(int timeLimitSec) {
            this.timeLimitSec = timeLimitSec;
        }
    }
}
