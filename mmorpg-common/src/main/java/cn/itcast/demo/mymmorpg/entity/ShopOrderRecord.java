package cn.itcast.demo.mymmorpg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "shop_order")
public class ShopOrderRecord {

    @Id
    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "product_type", nullable = false, length = 32)
    private String productType = "";

    @Column(name = "pay_amount", nullable = false)
    private Long payAmount = 0L;

    @Column(name = "currency", nullable = false, length = 16)
    private String currency = "CNY";

    @Column(name = "channel", nullable = false, length = 32)
    private String channel = "MOCK";

    @Column(name = "channel_sku", nullable = false, length = 64)
    private String channelSku = "";

    @Column(name = "channel_order_id", nullable = false, length = 128)
    private String channelOrderId = "";

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "paid_at", nullable = false)
    private Long paidAt = 0L;

    @Column(name = "fulfilled_at", nullable = false)
    private Long fulfilledAt = 0L;

    @Column(name = "idempotency_key", nullable = false, length = 191)
    private String idempotencyKey = "";

    @Lob
    @Column(name = "rewards_json")
    private String rewardsJson;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public Long getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(Long payAmount) {
        this.payAmount = payAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getChannelSku() {
        return channelSku;
    }

    public void setChannelSku(String channelSku) {
        this.channelSku = channelSku;
    }

    public String getChannelOrderId() {
        return channelOrderId;
    }

    public void setChannelOrderId(String channelOrderId) {
        this.channelOrderId = channelOrderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Long paidAt) {
        this.paidAt = paidAt;
    }

    public Long getFulfilledAt() {
        return fulfilledAt;
    }

    public void setFulfilledAt(Long fulfilledAt) {
        this.fulfilledAt = fulfilledAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRewardsJson() {
        return rewardsJson;
    }

    public void setRewardsJson(String rewardsJson) {
        this.rewardsJson = rewardsJson;
    }
}
