package ca.evanjones.cache;

import org.openjdk.jmh.annotations.*;

import java.util.function.Supplier;

// Run with: java -jar target/benchmarks.jar ExpireBenchmark -wi 3 -t 4 -i 4 -f 3
public class ExpireBenchmark {
    private static final Supplier<RaceChecker> supplier = new Supplier<RaceChecker>() {
        @Override
        public RaceChecker get() {
            return new RaceChecker(42);
        }
    };
    private static final ThreadLocalCache<RaceChecker> threadLocalCache = new ThreadLocalCache<>(supplier);
    private static final ExpiringThreadLocalCache<RaceChecker> expiringCache = new ExpiringThreadLocalCache<>(supplier);
    private static final SoftLocalCache<RaceChecker> softCache = new SoftLocalCache<>(supplier);
    private static final AtomicLocalCache<RaceChecker> atomicCache = new AtomicLocalCache<>(supplier);
    private static final CountingLocalCache<RaceChecker> countingCache = new CountingLocalCache<>(supplier);
    private static final PooledCache<RaceChecker> pooledCache = new PooledCache<>(supplier);

    private static RaceChecker test(Cache<RaceChecker> cache) {
        RaceChecker checker = cache.get();
        checker.check();
        return checker;
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void testExpiring() {
        test(expiringCache);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void testThreadLocal() {
        test(threadLocalCache);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void testSoftLocalCache() {
        test(softCache);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void testAtomicLocalCache() {
        test(atomicCache);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void testCountingLocalCache() {
        test(countingCache);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void testPooledCache() {
        RaceChecker obj = test(pooledCache);
        pooledCache.put(obj);
    }
}
