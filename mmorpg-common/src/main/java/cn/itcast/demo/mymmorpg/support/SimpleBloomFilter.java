package cn.itcast.demo.mymmorpg.support;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 简易布隆过滤器：挡掉 99% 不存在的图鉴/神瞳 Redis 穿透查询。
 */
@Component
public class SimpleBloomFilter {

    private final long[] bits;
    private final int bitSize;
    private final int hashCount;
    private final AtomicLong probes = new AtomicLong();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong negatives = new AtomicLong();

    public SimpleBloomFilter() {
        this(1 << 20, 5);
    }

    public SimpleBloomFilter(int expectedElements, double fpp) {
        int m = optimalBitSize(expectedElements, fpp);
        this.bitSize = m;
        this.bits = new long[(m + 63) >>> 6];
        this.hashCount = optimalHashCount(m, expectedElements);
    }

    public void add(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        long h1 = hash64(key);
        long h2 = hash64(key + "#2");
        for (int i = 0; i < hashCount; i++) {
            int pos = (int) Math.floorMod(h1 + i * h2, bitSize);
            bits[pos >>> 6] |= 1L << (pos & 63);
        }
    }

    /**
     * @return true 可能存在（需查 Redis/MySQL）；false 一定不存在
     */
    public boolean mightContain(String key) {
        probes.incrementAndGet();
        if (key == null || key.isBlank()) {
            negatives.incrementAndGet();
            return false;
        }
        long h1 = hash64(key);
        long h2 = hash64(key + "#2");
        for (int i = 0; i < hashCount; i++) {
            int pos = (int) Math.floorMod(h1 + i * h2, bitSize);
            if ((bits[pos >>> 6] & (1L << (pos & 63))) == 0) {
                negatives.incrementAndGet();
                return false;
            }
        }
        hits.incrementAndGet();
        return true;
    }

    public void clear() {
        Arrays.fill(bits, 0L);
    }

    public Map<String, Object> stats() {
        return Map.of(
                "bitSize", bitSize,
                "hashCount", hashCount,
                "probes", probes.get(),
                "hits", hits.get(),
                "negatives", negatives.get());
    }

    private static int optimalBitSize(int n, double p) {
        return Math.max(64, (int) Math.ceil(-n * Math.log(p) / (Math.log(2) * Math.log(2))));
    }

    private static int optimalHashCount(int m, int n) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    private static long hash64(String s) {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        long h = 0xcbf29ce484222325L;
        for (byte b : data) {
            h ^= b & 0xff;
            h *= 0x100000001b3L;
        }
        return h;
    }
}
