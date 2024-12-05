package io.helidon.service.registry;

public interface RegistryMetrics {
    int lookupCount();
    int fullScanCount();
    int cacheAccessCount();
    int cacheHitCount();
}
