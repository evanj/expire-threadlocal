# Expiring ThreadLocals

One problem with Java's [`ThreadLocal<T>`](https://docs.oracle.com/javase/8/docs/api/java/lang/ThreadLocal.html)
is that they are never freed until the thread exists. This can cause "memory leaks", if there are
lots of mostly idle threads. This can be a problem when using [NIO with heap ByteBuffers, which leaks
memory](http://www.evanjones.ca/java-bytebuffer-leak.html). Sometimes a thread will do a single I/O,
then do something else for a long time. This thread will hold on to an unused huge direct
ByteBuffer forever. This made me wonder if there are good policies for "expiring" ThreadLocals, so
I wrote this quick-and-dirty experiment.


## Conclusions

Use `ThreadLocal<SoftReference<T>>`. It has very low overhead compared to a plain `ThreadLocal`,
The JVM's [policy for freeing SoftReferences](http://jeremymanson.blogspot.com/2009/07/how-hotspot-decides-to-clear_07.html)
is not perfect, but it will eventually free unused caches in long running processes.
It also is more aggressive if the amount of free memory after a GC is low. This is a pretty
reasonable generic policy, as long as you [don't allocate large numbers of SoftReferences]
(http://bugs.java.com/bugdatabase/view_bug.do;jsessionid=cfd518f51afc7780e5188276b5f9?bug_id=6912889).
However, if you are caching complex and expensive objects (e.g. direct ByteBuffers), each thread
should also manage its own cache appropriately, such as by limiting the maximum size, or
shrinking the cache if it is larger than needed.

One untested idea: Estimate the maximum used size over a window of
N calls. If it is significantly less than the current allocated size (e.g. 1/2), then shrink.
(Note: if your cache grows by an exponential factor, you should shrink when the size is less then 1/factor to
provide hysteresis, [see Wikipedia on Dynamic Arrays](https://en.wikipedia.org/wiki/Dynamic_array#Geometric_expansion_and_amortized_cost)
for details.)

On each get, do something like:

```
maxUsed = max(maxUsed, currentRequest)
gets += 1
if gets >= N:
  recentMax = max(oldMaxUsed, maxUsed)
  if recentMax > allocated / 2:
    shrinkCacheTo(recentMax)
  oldMaxUsed = maxUsed
  maxUsed = 0
  gets = 0
```


## ExpiringThreadLocalCache

I wrote a class to demonstrate that you can create a policy where threads periodically scan all
created ThreadLocals to free unused caches. The challenge is that it is very hard to get the policy
right. If there are a mix of very active and less active threads, the active threads can scan too
often, causing the less active ones to be freed. Similarly, the overhead depends on how often
threads decide to scan. If the cache is very expensive and operations are relatively rare, you
need to scan after a low number of operations. If the operations are very frequent, you need a high
number.

As a result, I think the SoftReference approach is better, since it connects cache expiration to
garbage collection activity, which seems better in most cases. If the application as a whole is
very busy, it will garbage collect more often, which will eventually lead to idle SoftReferences
being freed. It doesn't matter how often the threads use the cache, it just matter how much the
application is allocating. It also helps that the code is pretty simple, and the cost is low.


## Running the Benchmarks

1. `mvn package`
2. `java -jar target/benchmarks.jar ExpireBenchmark -wi 3 -t 4 -i 4 -f 3`


## Example Results

```
Benchmark                                Mode  Cnt          Score          Error  Units
ExpireBenchmark.testAtomicLocalCache    thrpt   12  446592198.218 ± 14306464.504  ops/s
ExpireBenchmark.testCountingLocalCache  thrpt   12  393316475.897 ± 11911588.695  ops/s
ExpireBenchmark.testExpiring            thrpt   12  263807265.867 ±  8514802.259  ops/s
ExpireBenchmark.testPooledCache         thrpt   12    6597785.885 ±    76928.669  ops/s
ExpireBenchmark.testSoftLocalCache      thrpt   12  410208907.938 ±  7972137.382  ops/s
ExpireBenchmark.testThreadLocal         thrpt   12  525005786.953 ± 19702521.372  ops/s
```
