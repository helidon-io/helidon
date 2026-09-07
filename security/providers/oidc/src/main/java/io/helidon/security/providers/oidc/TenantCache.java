/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.security.providers.oidc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.LruCache;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.providers.oidc.common.TenantConfig;
import io.helidon.security.providers.oidc.common.spi.TenantConfigFinder;

final class TenantCache<T> {
    private final List<TenantConfigFinder> tenantConfigFinders;
    private final OidcConfig oidcConfig;
    private final Function<TenantConfig, Supplier<T>> valueFactory;
    private final LruCache<String, Supplier<T>> values = LruCache.create();
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Long> versions = new HashMap<>();

    TenantCache(List<TenantConfigFinder> tenantConfigFinders,
                OidcConfig oidcConfig,
                Function<TenantConfig, Supplier<T>> valueFactory) {
        this.tenantConfigFinders = List.copyOf(tenantConfigFinders);
        this.oidcConfig = oidcConfig;
        this.valueFactory = valueFactory;
        this.tenantConfigFinders.forEach(tenantConfigFinder -> tenantConfigFinder.onChange(this::remove));
    }

    Optional<Supplier<T>> get(String tenantId) {
        Optional<Supplier<T>> cachedValue = values.get(tenantId);
        if (cachedValue.isPresent()) {
            return cachedValue;
        }

        while (true) {
            long version;
            lock.lock();
            try {
                cachedValue = values.get(tenantId);
                if (cachedValue.isPresent()) {
                    return cachedValue;
                }
                version = versions.getOrDefault(tenantId, 0L);
            } finally {
                lock.unlock();
            }

            Optional<ResolvedTenantConfig> resolved = resolve(tenantId);
            Supplier<T> value;
            TenantConfig tenantConfig;
            lock.lock();
            try {
                if (version != versions.getOrDefault(tenantId, 0L)) {
                    continue;
                }
                if (resolved.isEmpty()) {
                    return Optional.empty();
                }
                ResolvedTenantConfig resolvedTenant = resolved.orElseThrow();
                tenantConfig = resolvedTenant.tenantConfig();
                value = values.get(resolvedTenant.cacheKey())
                        .orElseGet(() -> {
                            Supplier<T> newValue = valueFactory.apply(tenantConfig);
                            values.put(resolvedTenant.cacheKey(), newValue);
                            return newValue;
                        });
            } finally {
                lock.unlock();
            }

            if (!tenantConfig.tenantLoadingLazy()) {
                value.get();
            }
            return Optional.of(value);
        }
    }

    private Optional<ResolvedTenantConfig> resolve(String tenantId) {
        return tenantConfigFinders.stream()
                .map(tenantConfigFinder -> tenantConfigFinder.config(tenantId))
                .flatMap(Optional::stream)
                .map(tenantConfig -> new ResolvedTenantConfig(tenantId, tenantConfig))
                .findFirst()
                .or(() -> configuredTenantConfig(tenantId));
    }

    private Optional<ResolvedTenantConfig> configuredTenantConfig(String tenantId) {
        TenantConfig tenantConfig = oidcConfig.tenantConfig(tenantId);
        if (TenantConfigFinder.DEFAULT_TENANT_ID.equals(tenantId)
                || tenantId.equals(tenantConfig.name())) {
            return Optional.of(new ResolvedTenantConfig(tenantId, tenantConfig));
        }
        if (oidcConfig.fallbackToDefaultTenantEnabled()) {
            return Optional.of(new ResolvedTenantConfig(tenantConfig.name(), tenantConfig));
        }
        return Optional.empty();
    }

    private void remove(String tenantId) {
        lock.lock();
        try {
            versions.merge(tenantId, 1L, Long::sum);
            values.remove(tenantId);
        } finally {
            lock.unlock();
        }
    }

    private record ResolvedTenantConfig(String cacheKey, TenantConfig tenantConfig) {
    }
}
