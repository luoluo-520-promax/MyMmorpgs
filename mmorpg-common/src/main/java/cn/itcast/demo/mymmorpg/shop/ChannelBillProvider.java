package cn.itcast.demo.mymmorpg.shop;

import java.util.List;

/**
 * 渠道账单拉取 SPI：生产对接微信/支付宝/AppStore 对账单；开发用内存实现。
 */
public interface ChannelBillProvider {

    record ChannelBillLine(String channelOrderId, String merchantOrderId, long amountFen, String status) {
    }

    /**
     * @param channel 支付渠道标识
     * @param fromMs  账单起始（含）
     * @param toMs    账单结束（含）
     */
    List<ChannelBillLine> fetchBills(String channel, long fromMs, long toMs);

    default String channel() {
        return "DEFAULT";
    }
}
