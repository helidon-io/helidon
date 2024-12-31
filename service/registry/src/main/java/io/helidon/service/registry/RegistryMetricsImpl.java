/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.service.registry;

import java.util.concurrent.atomic.AtomicInteger;

final class RegistryMetricsImpl implements RegistryMetrics {
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
