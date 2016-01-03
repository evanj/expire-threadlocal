package ca.evanjones.cache;

import java.util.Objects;
import java.util.function.Supplier;

/** Estimates the cost of counting calls to get() in a ThreadLocal. */
public class CountingLocalCache<T> implements Cache<T> {
    private final Supplier<T> supplier;
    private final ThreadLocal<CountingReference<T>> cache = new ThreadLocal<>();

    public CountingLocalCache(Supplier<T> supplier) {
        this.supplier = Objects.requireNonNull(supplier);
    }

    private static final class CountingReference<T> {
        private final T obj;
        private int count;

        public CountingReference(T obj) {
            this.obj = Objects.requireNonNull(obj);
            count = 0;
        }

        public T get() {
            count += 1;
            return obj;
        }
    }

    @Override
    public T get() {
        CountingReference<T> ref = cache.get();
        if (ref != null) {
            T obj = ref.get();
            // this null check simulates expiration
            if (obj != null) {
                return obj;
            }
        }

        T obj = supplier.get();
        cache.set(new CountingReference<T>(obj));
        return obj;
    }
}
