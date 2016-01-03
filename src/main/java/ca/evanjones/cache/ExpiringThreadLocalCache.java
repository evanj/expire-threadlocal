package ca.evanjones.cache;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Thread-local cache that periodically scans for unused objects. Every N calls to get(), each thread increments
 * a global counter. Every few iterations of the global counter, a thread will attempt to scan for "unused" items.
 * This is a very quick-and-dirty implementation and probably has bugs and should not be used. It is very hard to
 * tune the policy to work in a variety of situations.
 */
public class ExpiringThreadLocalCache<T> implements Cache<T> {
    private final Supplier<T> supplier;
    private final ThreadLocal<ExpiringLocal<T>> threadLocal = new ThreadLocal<>();

    // use WeakReference so it is cleared when the threads exit
    private final ConcurrentLinkedQueue<WeakReference<ExpiringLocal<T>>> registered = new ConcurrentLinkedQueue<>();
    private final AtomicInteger registeredLength = new AtomicInteger(0);

    private final AtomicInteger globalIterations = new AtomicInteger(0);

    // every N gets, update the global counter and check if we need to expire
    private static final int LOCAL_CHECK_ITERATIONS = 100000;
    // every N global generations (per allocated cache item), scan for expired items
    private static final int GLOBAL_PER_LOCAL_ITERATIONS = 10;

    private static final class ExpiringLocal<T> {
        private final AtomicReference<T> ref;
        // must only be accessed by the o
        private final AtomicInteger getCount = new AtomicInteger(0);
        private final AtomicInteger lastScan = new AtomicInteger(-1);

        public ExpiringLocal(T obj) {
            this.ref = new AtomicReference<>(Objects.requireNonNull(obj));
        }

        public boolean trySetNull() {
            T result = ref.get();
            if (result == null) {
                return false;
            }
            return ref.weakCompareAndSet(result, null);
        }
    }

    public ExpiringThreadLocalCache(final Supplier<T> supplier) {
        this.supplier = Objects.requireNonNull(supplier);
    }

    private static final class CachedPair<T> {
        public final ExpiringLocal<T> local;
        public final T allocated;

        public CachedPair(ExpiringLocal<T> local, T allocated) {
            this.local = local;
            this.allocated = allocated;
        }
    }

    public CachedPair<T> newLocal() {
        T allocated = supplier.get();
        ExpiringLocal<T> local = new ExpiringLocal<>(allocated);
        registered.add(new WeakReference<>(local));
        registeredLength.getAndIncrement();
        threadLocal.set(local);

        return new CachedPair<T>(local, allocated);
    }

    private void expireCaches() {
        // scan caches for anything that should be expired
        Iterator<WeakReference<ExpiringLocal<T>>> it = registered.iterator();
        while (it.hasNext()) {
            ExpiringLocal local = it.next().get();
            if (local == null) {
                // weak reference was expired by thread exit and GC: remove it and move on
                it.remove();
                int v = registeredLength.decrementAndGet();
                assert(v >= 0);
                continue;
            }

            int count = local.getCount.get();
            int lastScan = local.lastScan.get();
            if (count == lastScan) {
                // appears to have not been used between scans: clear it and move on
                if (local.trySetNull()) {
                    // System.err.println("  WTF expired " + local + " count " + count + " last " + lastScan);
                    it.remove();
                    int v = registeredLength.decrementAndGet();
                    assert(v >= 0);
                }
            } else {
                // swap should only fail if another thread is also scanning: should be harmless
                local.lastScan.compareAndSet(lastScan, count);
            }
        }
    }

    @Override
    public T get() {
        T cached = null;
        ExpiringLocal<T> local = threadLocal.get();
        if (local == null) {
            // first time accessing the reference
            CachedPair<T> pair = newLocal();
            cached = pair.allocated;
            local = pair.local;
        } else {
            cached = local.ref.get();
            if (cached == null) {
                // someone cleared our reference: create a new one
                CachedPair<T> pair = newLocal();
                cached = pair.allocated;
                local = pair.local;
            }
        }
        assert(cached != null);

        // check if we need to expire other references
        int gets = local.getCount.incrementAndGet();
        if (gets >= LOCAL_CHECK_ITERATIONS) {
            //System.err.println("local check");
            // reset our count and increment the global count
            local.getCount.lazySet(0);
            // if we wrap between scans, we can't get accidentally have matching counter values
            local.lastScan.lazySet(-1);

            int globalGeneration = globalIterations.incrementAndGet();
            int locals = registeredLength.get();

            while (globalGeneration >= locals * GLOBAL_PER_LOCAL_ITERATIONS) {
                // we may need to expire the world: try to atomically reset the counter
                boolean didReset = globalIterations.compareAndSet(globalGeneration, 0);
                if (didReset) {
                    // System.err.println("WTF scanning gen:" + globalGeneration + " locals:" + locals);
                    expireCaches();
                    break;
                }
                globalGeneration = globalIterations.get();
                locals = registeredLength.get();
            }
        }

        return cached;
    }
}
