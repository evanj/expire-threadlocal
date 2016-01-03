package ca.evanjones.cache;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;

/** Only useful to estimate the cost of counting calls to get() in a ThreadLocal. */
public class PooledCache<T> implements Cache<T> {
    private final Supplier<T> supplier;
    private final ConcurrentLinkedDeque<T> cache = new ConcurrentLinkedDeque<>();

    public PooledCache(Supplier<T> supplier) {
        this.supplier = Objects.requireNonNull(supplier);
    }

    @Override
    public T get() {
        T obj = cache.pollLast();
        if (obj != null) {
            return obj;
        }

        return supplier.get();
    }

    public void put(T obj) {
        cache.addLast(obj);
    }
}
