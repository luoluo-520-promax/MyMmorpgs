/**
 * 文件说明：定时任务异常隔离装饰器单元测试。
 * 职责：验证 LogTask 捕获 delegate 异常且不向外抛出。
 */
package cn.itcast.demo.mymmorpg.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThatCode;

public class LogTaskTest {

    private static final Logger log = LoggerFactory.getLogger(LogTaskTest.class);

    @Test
    public void run_swallowsDelegateException() {
        String taskName = "buff-expire-task";
        AtomicBoolean executed = new AtomicBoolean(false);
        log.info("[测试开始] 场景=异常隔离 | taskName={} | delegateThrows=true", taskName);

        LogTask task = new LogTask(() -> {
            executed.set(true);
            throw new RuntimeException("模拟 Buff 到期任务失败");
        });

        assertThatCode(task::run).doesNotThrowAnyException();

        log.info("[测试断言] 场景=异常隔离 | executed={} | 期望=true", executed.get());
        assert executed.get();
    }

    @Test
    public void run_executesDelegateNormally() {
        AtomicBoolean executed = new AtomicBoolean(false);
        log.info("[测试开始] 场景=正常执行 | delegateThrows=false");

        LogTask task = new LogTask(() -> executed.set(true));
        task.run();

        log.info("[测试断言] 场景=正常执行 | executed={} | 期望=true", executed.get());
        assert executed.get();
    }
}
