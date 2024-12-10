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

/**
 * Metrics provided by the service registry.
 */
public sealed interface RegistryMetrics permits RegistryMetricsImpl {
    /**
     * Number of lookups done in the registry.
     *
     * @return lookup count
     */
    int lookupCount();

    /**
     * Number of full scan counts (going through every service in the registry and checking matches).
     *
     * @return full scan count
     */
    int fullScanCount();

    /**
     * Number of times we attempted to get a cached lookup.
     *
     * @return cache access count
     */
    int cacheAccessCount();

    /**
     * Number of times the cache provided a value.
     *
     * @return cache hit count
     */
    int cacheHitCount();
}
