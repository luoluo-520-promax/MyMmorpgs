package cn.itcast.demo.mymmorpg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/** 玩家任务进度 */
@Entity
@Table(name = "player_quest_progress")
@IdClass(PlayerQuestProgress.Pk.class)
public class PlayerQuestProgress {

    @Id
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Id
    @Column(name = "quest_id", nullable = false)
    private Integer questId;

    @Column(name = "status", nullable = false)
    private Integer status = 0;

    @Column(name = "progress", nullable = false)
    private Integer progress = 0;

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public Integer getQuestId() {
        return questId;
    }

    public void setQuestId(Integer questId) {
        this.questId = questId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public static class Pk implements Serializable {
        private Long playerId;
        private Integer questId;

        public Pk() {
        }

        public Pk(Long playerId, Integer questId) {
            this.playerId = playerId;
            this.questId = questId;
        }

        public Long getPlayerId() {
            return playerId;
        }

        public void setPlayerId(Long playerId) {
            this.playerId = playerId;
        }

        public Integer getQuestId() {
            return questId;
        }

        public void setQuestId(Integer questId) {
            this.questId = questId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Pk pk)) {
                return false;
            }
            return Objects.equals(playerId, pk.playerId) && Objects.equals(questId, pk.questId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerId, questId);
        }
    }
}
