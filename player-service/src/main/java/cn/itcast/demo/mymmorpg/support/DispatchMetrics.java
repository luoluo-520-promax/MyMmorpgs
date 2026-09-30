package cn.itcast.demo.mymmorpg.support;

import cn.itcast.demo.mymmorpg.support.DispatchFailureReporter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class DispatchMetrics implements DispatchFailureReporter {

    private final Counter rpcForwardFailures;
    private final Counter pushFailures;
    private final Counter mqPublishFailures;

    public DispatchMetrics(MeterRegistry registry) {
        this.rpcForwardFailures = Counter.builder("mmorpg.dispatch.rpc_forward_failures")
                .register(registry);
        this.pushFailures = Counter.builder("mmorpg.dispatch.push_failures")
                .register(registry);
        this.mqPublishFailures = Counter.builder("mmorpg.mq.publish_failures")
                .register(registry);
    }

    @Override
    public void recordRpcForwardFailure() {
        rpcForwardFailures.increment();
    }

    @Override
    public void recordPushFailure() {
        pushFailures.increment();
    }

    @Override
    public void recordMqPublishFailure() {
        mqPublishFailures.increment();
    }
}
