package cn.itcast.demo.mymmorpg.shop;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 开发/测试用渠道账单：可手工 seed，供对账差异对比。
 */
@Component
public class InMemoryChannelBillProvider implements ChannelBillProvider {

    private final CopyOnWriteArrayList<ChannelBillLine> lines = new CopyOnWriteArrayList<>();

    public void seed(ChannelBillLine line) {
        if (line != null) {
            lines.add(line);
        }
    }

    public void clear() {
        lines.clear();
    }

    @Override
    public List<ChannelBillLine> fetchBills(String channel, long fromMs, long toMs) {
        return new ArrayList<>(lines);
    }

    @Override
    public String channel() {
        return "IN_MEMORY";
    }
}
