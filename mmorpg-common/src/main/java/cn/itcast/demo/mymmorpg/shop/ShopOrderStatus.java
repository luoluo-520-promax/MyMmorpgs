package cn.itcast.demo.mymmorpg.shop;

/**
 * 商城订单状态。对外文档口径 DELIVERED ≡ FULFILLED（履约完成）。
 */
public enum ShopOrderStatus {
    CREATED,
    PAID,
    /** 履约完成（历史名，仍写入兼容） */
    FULFILLED,
    /** 报告标准名，与 FULFILLED 等价；新写入优先使用 */
    DELIVERED,
    CLOSED,
    REFUNDED;

    public static boolean isDelivered(String status) {
        return FULFILLED.name().equals(status) || DELIVERED.name().equals(status);
    }

    public static boolean canRefund(String status) {
        return isDelivered(status) || PAID.name().equals(status);
    }
}
