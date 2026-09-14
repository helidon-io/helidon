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

import java.util.List;
import java.util.Optional;

import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.providers.oidc.common.TenantConfig;
import io.helidon.security.providers.oidc.common.spi.TenantConfigFinder;

final class TenantConfigResolver {

    private TenantConfigResolver() {
    }

    static Optional<ResolvedTenantConfig> resolve(List<TenantConfigFinder> tenantConfigFinders,
                                                  OidcConfig oidcConfig,
                                                  String tenantId) {
        return tenantConfigFinders.stream()
                .map(tenantConfigFinder -> tenantConfigFinder.config(tenantId))
                .flatMap(Optional::stream)
                .map(tenantConfig -> new ResolvedTenantConfig(tenantId, tenantConfig))
                .findFirst()
                .or(() -> configuredTenantConfig(oidcConfig, tenantId));
    }

    private static Optional<ResolvedTenantConfig> configuredTenantConfig(OidcConfig oidcConfig, String tenantId) {
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

    record ResolvedTenantConfig(String cacheKey, TenantConfig tenantConfig) {
    }
}
