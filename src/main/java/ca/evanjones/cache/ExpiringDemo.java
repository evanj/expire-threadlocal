package ca.evanjones.cache;


import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Generates garbage to demonstrate that the caches will eventually free data from idle threads.
 * java -Xmx50M -Xms50M -XX:+PrintGC -XX:+PrintGCDateStamps -XX:+PrintGCDetails ExpiringDemo
 */
public class ExpiringDemo {
    private static final ConcurrentLinkedQueue<String> leaked = new ConcurrentLinkedQueue<String>();
    private static final int LEAKED_LIMIT = 8000;


    private static final class CachedObject {
        private static final AtomicInteger aliveCount = new AtomicInteger();

        private int value;

        public CachedObject(int value) {
            this.value = value;
            int allocated = aliveCount.incrementAndGet();
            System.out.println(Thread.currentThread() + " allocated total: " + allocated);
        }

        public void finalize() {
            int allocated = aliveCount.decrementAndGet();
            System.out.println("Finalized allocated total: " + allocated);
        }

        public int increment() {
            value += 1;
            return value;
        }
    }

    static void doWork(Cache<CachedObject> cache) {
        CachedObject obj = cache.get();
        int v = obj.increment();
        String[] values = new String[128];
        for (int i = 0; i < values.length; i++) {
            values[i] = makeBigString(i);
        }
        obj = null;

        // probability of leaking an object per call to doWork()
        double probability = 0.90/values.length;
        boolean added = false;
        for (int i = 0; i < values.length; i++) {
            if (ThreadLocalRandom.current().nextDouble() < probability) {
                leaked.add(values[i]);
                added = true;
            }
        }

        if (added) {
            // int len = leaked.size();
            // System.out.println("!!! size:" + len);
            if (leaked.size() > LEAKED_LIMIT) {
                // System.out.println("!!!! removing elements");
                for (int i = 0; i < 100; i++) {
                    leaked.poll();
                }
            }
        }
    }

    private static final class BusyThread implements Runnable {
        private final Cache<CachedObject> cache;

        public BusyThread(Cache<CachedObject> cache) {
            this.cache = Objects.requireNonNull(cache);
        }

        public void run() {
            while (true) {
                doWork(cache);
                try {
                    int sleepMs = ThreadLocalRandom.current().nextInt(50, 150);
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static final class IdleThread implements Runnable {
        private static final int MIN_SLEEP_SECONDS = 2 * 60;
        private static final int MAX_SLEEP_SECONDS = 15 * 60;

        private final Cache<CachedObject> cache;

        public IdleThread(Cache<CachedObject> cache) {
            this.cache = Objects.requireNonNull(cache);
        }

        @Override
        public void run() {
            while (true) {
                doWork(cache);
                try {
                    int sleepSecs = ThreadLocalRandom.current().nextInt(MIN_SLEEP_SECONDS, MAX_SLEEP_SECONDS);
                    Thread.sleep(sleepSecs * 1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static String makeBigString(int value) {
        String bigString = "";
        for (int i = 0; i < 50; i++) {
            bigString += String.format("leaked string %d %d", i, value);
        }
        return bigString;
    }

    public static void main(String[] arguments) {
        BusyThread[] busy = new BusyThread[4];
        IdleThread[] idle = new IdleThread[100];
        Supplier<CachedObject> supplier = new Supplier<CachedObject>() {
            @Override
            public CachedObject get() {
                return new CachedObject(0);
            }
        };
        Cache<CachedObject> cache = new SoftLocalCache<>(supplier);

        // Leak a bunch of strings to try and fill the old gen
        for (int i = 0; i < LEAKED_LIMIT; i++) {
            leaked.add(makeBigString(i));
        }

        for (int i = 0; i < busy.length; i++) {
            busy[i] = new BusyThread(cache);
            Thread t = new Thread(busy[i]);
            t.start();
        }
        for (int i = 0; i < idle.length; i++) {
            idle[i] = new IdleThread(cache);
            Thread t = new Thread(idle[i]);
            t.start();
        }
    }
}
