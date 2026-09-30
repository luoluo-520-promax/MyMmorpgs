package cn.itcast.demo.mymmorpg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 限量商品全局库存：Redis Lua 原子扣减，防止超卖；库存归零时回调通知（可投 MQ 同步活动展示）。
 */
@Service
public class ShopStockService {

    private static final Logger log = LoggerFactory.getLogger(ShopStockService.class);
    private static final String STOCK_KEY = "shop:stock:";

    private static final String DEDUCT_LUA = """
            local key = KEYS[1]
            local qty = tonumber(ARGV[1])
            local cur = tonumber(redis.call('GET', key) or '-1')
            if cur < 0 then
              return -2
            end
            if cur < qty then
              return -1
            end
            local left = redis.call('DECRBY', key, qty)
            return left
            """;

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> deductScript;
    private final ObjectProvider<Consumer<StockDepletedEvent>> stockDepletedListeners;

    public ShopStockService(StringRedisTemplate stringRedisTemplate,
                            ObjectProvider<Consumer<StockDepletedEvent>> stockDepletedListeners) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.stockDepletedListeners = stockDepletedListeners;
        this.deductScript = new DefaultRedisScript<>();
        this.deductScript.setScriptText(DEDUCT_LUA);
        this.deductScript.setResultType(Long.class);
    }

    public void initStock(int productId, int stock) {
        if (productId <= 0 || stock < 0) {
            return;
        }
        stringRedisTemplate.opsForValue().set(STOCK_KEY + productId, String.valueOf(stock));
    }

    public long remaining(int productId) {
        String raw = stringRedisTemplate.opsForValue().get(STOCK_KEY + productId);
        if (raw == null || raw.isBlank()) {
            return -1L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * @return remaining after deduct; -1 insufficient; -2 not configured (unlimited)
     */
    public long tryDeduct(int productId, int qty) {
        if (productId <= 0 || qty <= 0) {
            return -1L;
        }
        Long left = stringRedisTemplate.execute(
                deductScript, List.of(STOCK_KEY + productId), String.valueOf(qty));
        long result = left == null ? -1L : left;
        if (result == 0L) {
            notifyDepleted(productId);
        }
        return result;
    }

    public void restore(int productId, int qty) {
        if (productId <= 0 || qty <= 0) {
            return;
        }
        stringRedisTemplate.opsForValue().increment(STOCK_KEY + productId, qty);
    }

    private void notifyDepleted(int productId) {
        StockDepletedEvent event = new StockDepletedEvent(productId, System.currentTimeMillis());
        stockDepletedListeners.orderedStream().forEach(listener -> {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.warn("stock depleted listener failed productId={}", productId, e);
            }
        });
        log.info("shop stock depleted productId={}", productId);
    }

    public record StockDepletedEvent(int productId, long atMs) {
    }
}
