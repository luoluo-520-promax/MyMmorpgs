package cn.itcast.demo.mymmorpg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "player_skin_owned")
@IdClass(PlayerSkinOwned.Pk.class)
public class PlayerSkinOwned {

    @Id
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Id
    @Column(name = "skin_id", nullable = false)
    private Integer skinId;

    @Column(name = "obtained_at", nullable = false)
    private Long obtainedAt;

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public Integer getSkinId() {
        return skinId;
    }

    public void setSkinId(Integer skinId) {
        this.skinId = skinId;
    }

    public Long getObtainedAt() {
        return obtainedAt;
    }

    public void setObtainedAt(Long obtainedAt) {
        this.obtainedAt = obtainedAt;
    }

    public static class Pk implements Serializable {
        private Long playerId;
        private Integer skinId;

        public Pk() {
        }

        public Pk(Long playerId, Integer skinId) {
            this.playerId = playerId;
            this.skinId = skinId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Pk pk)) {
                return false;
            }
            return Objects.equals(playerId, pk.playerId) && Objects.equals(skinId, pk.skinId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerId, skinId);
        }
    }
}
