package ca.evanjones.cache;

import java.util.function.Supplier;

/** Caches objects using ThreadLocal<T>. */
public class ThreadLocalCache<T> implements Cache<T> {
    private final ThreadLocal<T> cache;

    public ThreadLocalCache(Supplier<T> supplier) {
        cache = ThreadLocal.withInitial(supplier);
    }

    @Override
    public T get() {
        return cache.get();
    }
}
