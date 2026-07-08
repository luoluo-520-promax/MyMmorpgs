/**
 * 通用懒加载单例 holder：用于 Netty Channel、调度器等非 Spring 管理的组件，
 * 双重检查锁保证多线程下只创建一次实例。
 */
package cn.itcast.demo.mymmorpg.support;

import java.util.Objects; // requireNonNull 校验 supplier
import java.util.function.Supplier; // 延迟创建实例的工厂

public final class Singleton<T> {

    private final Supplier<T> supplier; // 首次 get() 时调用的构造逻辑
    private volatile T instance; // volatile 保证 DCL 可见性

    private Singleton(Supplier<T> supplier) {
        this.supplier = Objects.requireNonNull(supplier, "supplier"); // supplier 不可为 null
    }

    /** 创建 Singleton 包装，传入 lazy 构造逻辑 */
    public static <T> Singleton<T> of(Supplier<T> supplier) {
        return new Singleton<>(supplier);
    }

    /** 获取单例：已创建则直接返回，否则 synchronized 内 double-check 后创建 */
    public T get() {
        T v = instance; // 第一次无锁读，热路径避免 synchronized
        if (v != null) {
            return v;
        }
        synchronized (this) { // 仅首次创建时加锁
            v = instance; // 进入锁后再次检查，防止重复创建
            if (v == null) {
                v = supplier.get(); // 执行实际构造（如 new GameSessionManager()）
                instance = v; // 发布引用，对其他线程可见
            }
        }
        return v;
    }
}
