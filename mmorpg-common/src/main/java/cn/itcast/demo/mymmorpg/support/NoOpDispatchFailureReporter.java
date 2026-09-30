package cn.itcast.demo.mymmorpg.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(DispatchFailureReporter.class)
public class NoOpDispatchFailureReporter implements DispatchFailureReporter {
}
