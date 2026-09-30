package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.support.ShopMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/**
 * 自动对账调度：调用 {@link ShopService#reconcile(long, long)}，记录差异与指标，超阈值告警。
 * <p>
 * 默认关闭（{@code shop.reconcile.auto-enabled=false}），避免单测/本地启动误跑。
 */
@Component
@ConditionalOnProperty(name = "shop.reconcile.auto-enabled", havingValue = "true")
public class AutoReconcileScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoReconcileScheduler.class);

    private final ShopService shopService;
    private final ObjectProvider<ShopMetrics> shopMetrics;
    private final ObjectProvider<AutoReconcileHandler> autoReconcileHandler;
    private final long lookbackMs;
    private final int mismatchAlertThreshold;

    public AutoReconcileScheduler(ShopService shopService,
                                  ObjectProvider<ShopMetrics> shopMetrics,
                                  ObjectProvider<AutoReconcileHandler> autoReconcileHandler,
                                  @Value("${shop.reconcile.lookback-ms:86400000}") long lookbackMs,
                                  @Value("${shop.reconcile.mismatch-alert-threshold:5}") int mismatchAlertThreshold) {
        this.shopService = shopService;
        this.shopMetrics = shopMetrics;
        this.autoReconcileHandler = autoReconcileHandler;
        this.lookbackMs = Math.max(60_000L, lookbackMs);
        this.mismatchAlertThreshold = Math.max(0, mismatchAlertThreshold);
    }

    /**
     * 默认每小时整点；可用 {@code shop.reconcile.cron} 改为日终等（例如 {@code 0 0 2 * * *}）。
     */
    @Scheduled(cron = "${shop.reconcile.cron:0 0 * * * *}")
    public void reconcileScheduled() {
        long toMs = System.currentTimeMillis();
        long fromMs = toMs - lookbackMs;
        Map<String, Object> result;
        try {
            result = shopService.reconcile(fromMs, toMs);
        } catch (Exception e) {
            log.error("auto reconcile failed fromMs={} toMs={}", fromMs, toMs, e);
            ShopMetrics metrics = shopMetrics.getIfAvailable();
            if (metrics != null) {
                metrics.recordReconcileFailure();
            }
            return;
        }

        int mismatch = mismatchCount(result);
        ShopMetrics metrics = shopMetrics.getIfAvailable();
        if (metrics != null) {
            metrics.recordReconcileRun(mismatch);
        }

        log.info("auto reconcile ok={} paidStuck={} delivered={} billLines={} mismatch={} diffs channelOnly={} localOnly={} statusMismatch={} missingChannel={}",
                result.get("ok"),
                result.get("paidStuckCount"),
                result.get("deliveredCount"),
                result.get("channelBillLineCount"),
                mismatch,
                sizeOf(result.get("diffChannelOnly")),
                sizeOf(result.get("diffLocalOnly")),
                sizeOf(result.get("diffPaidStuckVsChannelSuccess")),
                sizeOf(result.get("missingChannelOrderIds")));

        if (mismatch > mismatchAlertThreshold) {
            log.error("RECONCILE_ALERT mismatchCount={} threshold={} fromMs={} toMs={} channelOnly={} localOnly={} statusMismatch={} paidStuck={} missingChannel={}",
                    mismatch, mismatchAlertThreshold, fromMs, toMs,
                    result.get("diffChannelOnly"),
                    result.get("diffLocalOnly"),
                    result.get("diffPaidStuckVsChannelSuccess"),
                    result.get("paidStuckOrderIds"),
                    result.get("missingChannelOrderIds"));
            if (metrics != null) {
                metrics.recordReconcileAlert();
            }
        }

        AutoReconcileHandler handler = autoReconcileHandler.getIfAvailable();
        if (handler != null) {
            Map<String, Object> auto = handler.handleAfterReconcile(result);
            log.info("auto reconcile fulfill ok={} autoFixed={} autoFailed={} manual={}",
                    auto.get("ok"), auto.get("autoFixed"), auto.get("autoFailed"), auto.get("manualQueueSize"));
        }
    }

    @SuppressWarnings("unchecked")
    static int mismatchCount(Map<String, Object> result) {
        if (result == null) {
            return 0;
        }
        int n = 0;
        Object stuck = result.get("paidStuckCount");
        if (stuck instanceof Number num) {
            n += num.intValue();
        }
        n += sizeOf(result.get("missingChannelOrderIds"));
        n += sizeOf(result.get("diffChannelOnly"));
        n += sizeOf(result.get("diffLocalOnly"));
        n += sizeOf(result.get("diffPaidStuckVsChannelSuccess"));
        return n;
    }

    private static int sizeOf(Object value) {
        if (value instanceof Collection<?> c) {
            return c.size();
        }
        return 0;
    }
}
