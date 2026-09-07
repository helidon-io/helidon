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

package io.helidon.security.providers.oidc.common;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.json.JsonObject;
import io.helidon.security.providers.oidc.common.spi.TenantConfigFinder;

final class OidcAuthenticationValidator {
    private OidcAuthenticationValidator() {
    }

    static void validate(OidcConfig oidcConfig,
                         Map<String, TenantConfig> tenantConfigurations,
                         Supplier<Tenant> defaultTenant) {
        TenantConfig configuredDefault = tenantConfigurations.get(TenantConfigFinder.DEFAULT_TENANT_ID);
        if (configuredDefault == null) {
            validateKnownTenant(oidcConfig, defaultTenant);
        }
        tenantConfigurations.values().forEach(tenantConfig ->
                validateKnownTenant(tenantConfig, () -> Tenant.create(oidcConfig, tenantConfig)));
    }

    static void validate(TenantConfig tenantConfig) {
        Objects.requireNonNull(tenantConfig);
        validateJwkFaultTolerance(tenantConfig);
        if (!tenantConfig.validateJwtWithJwk()
                || tenantConfig.tenantSignJwk().isPresent()
                || tenantConfig.tenantSignJwkResource().isPresent()
                || tenantConfig.oidcMetadataResource().isPresent()) {
            return;
        }

        JsonObject metadata = tenantConfig.oidcMetadataJsonObject();
        if (metadata == null) {
            if (!tenantConfig.useWellKnown()) {
                throw new IllegalArgumentException(
                        "A signing JWK source is required when JWT validation with JWK is enabled");
            }
            return;
        }

        String key = OidcUtil.resolveMetaKey("jwks_uri", tenantConfig.serverType(), tenantConfig.identityUri());
        String jwkEndpoint = metadata.stringValue(key).orElse(null);
        if (jwkEndpoint == null) {
            throw new IllegalArgumentException(
                    "OIDC metadata must contain a JWK URI when JWT validation with JWK is enabled");
        }
        OidcUtil.validateHttpEndpoint(jwkEndpoint, "OIDC metadata JWK endpoint");
    }

    private static void validateJwkFaultTolerance(TenantConfig tenantConfig) {
        Duration timeout = Objects.requireNonNull(tenantConfig.jwkTimeout()).prototype().timeout();
        Duration retryTimeout = Objects.requireNonNull(tenantConfig.jwkRetry()).prototype().overallTimeout();
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("jwk-loader.timeout.timeout must be positive");
        }
        if (timeout.compareTo(retryTimeout) > 0) {
            throw new IllegalArgumentException("jwk-loader.timeout.timeout must not exceed "
                                                       + "jwk-loader.retry.overall-timeout");
        }
    }

    private static void validateKnownTenant(TenantConfig tenantConfig, Supplier<Tenant> tenantSupplier) {
        validate(tenantConfig);
        if (!tenantConfig.tenantLoadingLazy()) {
            tenantSupplier.get();
        }
    }
}
