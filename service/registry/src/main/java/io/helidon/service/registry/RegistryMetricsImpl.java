package io.helidon.service.registry;

import java.util.concurrent.atomic.AtomicInteger;

class RegistryMetricsImpl implements RegistryMetrics {
    private final AtomicInteger lookupCount = new AtomicInteger();
    private final AtomicInteger fullScanCount = new AtomicInteger();
    private final AtomicInteger cacheAccessCount = new AtomicInteger();
    private final AtomicInteger cacheHitCount = new AtomicInteger();


    @Override
    public int lookupCount() {
        return lookupCount.get();
    }

    @Override
    public int fullScanCount() {
        return fullScanCount.get();
    }

    @Override
    public int cacheAccessCount() {
        return cacheAccessCount.get();
    }

    @Override
    public int cacheHitCount() {
        return cacheHitCount.get();
    }

    void lookup() {
        lookupCount.incrementAndGet();
    }

    void fullScan() {
        fullScanCount.incrementAndGet();
    }

    void cacheAccess() {
        cacheAccessCount.incrementAndGet();
    }

    void cacheHit() {
        cacheHitCount.incrementAndGet();
    }
}
