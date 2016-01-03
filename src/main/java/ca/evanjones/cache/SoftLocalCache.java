package ca.evanjones.cache;

import java.lang.ref.SoftReference;
import java.util.Objects;
import java.util.function.Supplier;

/** Caches objects in a ThreadLocal that uses a SoftReference so they will eventually be cleared. */
public class SoftLocalCache<T> implements Cache<T> {
    private final Supplier<T> supplier;
    private final ThreadLocal<SoftReference<T>> cache = new ThreadLocal<SoftReference<T>>();

    public SoftLocalCache(Supplier<T> supplier) {
        this.supplier = Objects.requireNonNull(supplier);
    }

    @Override
    public T get() {
        SoftReference<T> ref = cache.get();
        if (ref != null) {
            T obj = ref.get();
            if (obj != null) {
                return obj;
            }
        }

        T obj = supplier.get();
        cache.set(new SoftReference<T>(obj));
        return obj;
    }
}
