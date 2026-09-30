package cn.itcast.demo.mymmorpg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "item_ledger")
public class ItemLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "item_config_id", nullable = false)
    private Integer itemConfigId;

    @Column(name = "delta", nullable = false)
    private Integer delta;

    @Column(name = "count_before", nullable = false)
    private Integer countBefore;

    @Column(name = "count_after", nullable = false)
    private Integer countAfter;

    @Column(name = "biz_type", nullable = false, length = 64)
    private String bizType;

    @Column(name = "biz_no", nullable = false, length = 128)
    private String bizNo;

    @Column(name = "idempotency_key", nullable = false, length = 191)
    private String idempotencyKey;

    @Column(name = "operator", nullable = false, length = 64)
    private String operator = "system";

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public Integer getItemConfigId() {
        return itemConfigId;
    }

    public void setItemConfigId(Integer itemConfigId) {
        this.itemConfigId = itemConfigId;
    }

    public Integer getDelta() {
        return delta;
    }

    public void setDelta(Integer delta) {
        this.delta = delta;
    }

    public Integer getCountBefore() {
        return countBefore;
    }

    public void setCountBefore(Integer countBefore) {
        this.countBefore = countBefore;
    }

    public Integer getCountAfter() {
        return countAfter;
    }

    public void setCountAfter(Integer countAfter) {
        this.countAfter = countAfter;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
