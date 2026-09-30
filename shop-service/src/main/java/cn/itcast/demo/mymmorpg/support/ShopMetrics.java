package cn.itcast.demo.mymmorpg.support;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ShopMetrics {

    private final Counter ordersCreated;
    private final Counter ordersFulfilled;
    private final Counter payVerifyFailures;
    private final Counter refunds;
    private final Counter reconcileRuns;
    private final Counter reconcileFailures;
    private final Counter reconcileAlerts;
    private final AtomicInteger lastReconcileMismatch = new AtomicInteger();

    public ShopMetrics(MeterRegistry registry) {
        this.ordersCreated = Counter.builder("mmorpg.shop.orders_created").register(registry);
        this.ordersFulfilled = Counter.builder("mmorpg.shop.orders_fulfilled").register(registry);
        this.payVerifyFailures = Counter.builder("mmorpg.shop.pay_verify_failures").register(registry);
        this.refunds = Counter.builder("mmorpg.shop.refunds").register(registry);
        this.reconcileRuns = Counter.builder("mmorpg.shop.reconcile_runs").register(registry);
        this.reconcileFailures = Counter.builder("mmorpg.shop.reconcile_failures").register(registry);
        this.reconcileAlerts = Counter.builder("mmorpg.shop.reconcile_alerts").register(registry);
        Gauge.builder("mmorpg.shop.reconcile_mismatch", lastReconcileMismatch, AtomicInteger::get)
                .register(registry);
    }

    public void recordOrderCreated() {
        ordersCreated.increment();
    }

    public void recordFulfilled() {
        ordersFulfilled.increment();
    }

    public void recordPayVerifyFailure() {
        payVerifyFailures.increment();
    }

    public void recordRefund() {
        refunds.increment();
    }

    public void recordReconcileRun(int mismatchCount) {
        reconcileRuns.increment();
        lastReconcileMismatch.set(Math.max(0, mismatchCount));
    }

    public void recordReconcileFailure() {
        reconcileFailures.increment();
    }

    public void recordReconcileAlert() {
        reconcileAlerts.increment();
    }
}
