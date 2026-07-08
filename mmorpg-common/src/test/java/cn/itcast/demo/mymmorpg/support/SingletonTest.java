/**
 * 文件说明：懒加载单例 holder 单元测试。
 * 职责：验证 Singleton 双重检查锁只创建一次实例。
 */
package cn.itcast.demo.mymmorpg.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class SingletonTest {

    private static final Logger log = LoggerFactory.getLogger(SingletonTest.class);

    @Test
    public void get_createsInstanceOnce() {
        AtomicInteger counter = new AtomicInteger();
        Singleton<String> singleton = Singleton.of(() -> {
            counter.incrementAndGet();
            return "mmorpg-node";
        });
        log.info("[测试开始] 场景=单例只创建一次 | supplier初始计数={}", counter.get());

        String first = singleton.get();
        String second = singleton.get();

        log.info("[测试断言] 场景=单例只创建一次 | first={} | second={} | createCount={} | 期望createCount=1",
                first, second, counter.get());
        assertThat(first).isSameAs(second);
        assertThat(counter.get()).isEqualTo(1);
    }
}
