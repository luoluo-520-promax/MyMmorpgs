package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.AdminAiPlatformClient;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.Iterator;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 单测用空 ObjectProvider，避免依赖 Mockito 完整 Spring 容器。
 */
final class EmptyAiPlatformClientProvider implements ObjectProvider<AdminAiPlatformClient> {

    static EmptyAiPlatformClientProvider INSTANCE = new EmptyAiPlatformClientProvider();

    @Override
    public AdminAiPlatformClient getObject(Object... args) {
        return null;
    }

    @Override
    public AdminAiPlatformClient getObject() {
        return null;
    }

    @Override
    public AdminAiPlatformClient getIfAvailable() {
        return null;
    }

    @Override
    public AdminAiPlatformClient getIfAvailable(Supplier<AdminAiPlatformClient> defaultSupplier) {
        return defaultSupplier.get();
    }

    @Override
    public AdminAiPlatformClient getIfUnique() {
        return null;
    }

    @Override
    public AdminAiPlatformClient getIfUnique(Supplier<AdminAiPlatformClient> defaultSupplier) {
        return defaultSupplier.get();
    }

    @Override
    public Stream<AdminAiPlatformClient> stream() {
        return Stream.empty();
    }

    @Override
    public Stream<AdminAiPlatformClient> orderedStream() {
        return Stream.empty();
    }

    @Override
    public Iterator<AdminAiPlatformClient> iterator() {
        return Collections.emptyIterator();
    }
}
