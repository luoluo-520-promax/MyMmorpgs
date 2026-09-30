package cn.itcast.demo.mymmorpg.config;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 配置快照根：Build 阶段构建完整不可变树，Swap 阶段原子切换指针。
 */
public final class ConfigSnapshotRoot<T> {

    private final AtomicReference<T> rootRef;

    public ConfigSnapshotRoot(T initial) {
        this.rootRef = new AtomicReference<>(initial);
    }

    public T get() {
        return rootRef.get();
    }

    /** 仅当校验通过后调用，原子切换根指针。 */
    public void swap(T next) {
        if (next == null) {
            throw new IllegalArgumentException("config snapshot root cannot be null");
        }
        rootRef.set(next);
    }

    public static <K, V> Map<K, V> emptyMap() {
        return Collections.emptyMap();
    }
}
