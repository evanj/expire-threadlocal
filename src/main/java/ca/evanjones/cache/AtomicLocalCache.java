package ca.evanjones.cache;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Estimates the cost of putting AtomicReference in a ThreadLocal. */
public class AtomicLocalCache<T> implements Cache<T> {
    private final Supplier<T> supplier;
    private final ThreadLocal<AtomicReference<T>> cache = new ThreadLocal<>();

    public AtomicLocalCache(Supplier<T> supplier) {
        this.supplier = Objects.requireNonNull(supplier);
    }

    @Override
    public T get() {
        AtomicReference<T> ref = cache.get();
        if (ref != null) {
            T obj = ref.get();
            // simulates expiration (like a SoftReference)
            if (obj != null) {
                return obj;
            }
        }

        T obj = supplier.get();
        cache.set(new AtomicReference<>(obj));
        return obj;
    }
}
