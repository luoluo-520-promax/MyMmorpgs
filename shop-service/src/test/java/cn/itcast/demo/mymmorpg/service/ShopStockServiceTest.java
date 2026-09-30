package cn.itcast.demo.mymmorpg.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 限量库存 Lua 扣减 + 归零通知流程。
 */
public class ShopStockServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    public void tryDeduct_insufficientReturnsMinusOne() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(-1L);
        ShopStockService stock = new ShopStockService(redis, mock(org.springframework.beans.factory.ObjectProvider.class));
        assertThat(stock.tryDeduct(1001, 1)).isEqualTo(-1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void tryDeduct_toZero_notifiesListeners() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
        AtomicReference<ShopStockService.StockDepletedEvent> captured = new AtomicReference<>();
        Consumer<ShopStockService.StockDepletedEvent> listener = captured::set;
        org.springframework.beans.factory.ObjectProvider<Consumer<ShopStockService.StockDepletedEvent>> provider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.orderedStream()).thenReturn(java.util.stream.Stream.of(listener));

        ShopStockService stock = new ShopStockService(redis, provider);
        assertThat(stock.tryDeduct(2002, 1)).isEqualTo(0L);
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().productId()).isEqualTo(2002);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void initAndRemaining() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("shop:stock:9")).thenReturn("42");

        ShopStockService stock = new ShopStockService(redis, mock(org.springframework.beans.factory.ObjectProvider.class));
        stock.initStock(9, 100);
        verify(ops).set(eq("shop:stock:9"), eq("100"));
        assertThat(stock.remaining(9)).isEqualTo(42L);
    }
}
