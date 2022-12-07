/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import io.helidon.config.Config;
import io.helidon.security.ProviderRequest;
import io.helidon.security.providers.oidc.common.spi.TenantIdFinder;
import io.helidon.security.providers.oidc.common.spi.TenantIdProvider;
import io.helidon.security.util.TokenHandler;

import jakarta.annotation.Priority;

import static io.helidon.security.providers.oidc.common.spi.TenantConfigFinder.DEFAULT_TENANT_ID;

/**
 * This is the default tenant id provider.
 */
@Priority(100000)
class DefaultTenantIdProvider implements TenantIdProvider {

    static final String DEFAULT_TENANT_ID_STYLE = "host-header";
    static final Optional<String> DEFAULT_TENANT_ID_VALUE = Optional.of(DEFAULT_TENANT_ID);

    @Override
    public TenantIdFinder createTenantIdFinder(Config config) {
        boolean multiTenant = config.get("multi-tenant").asBoolean().orElse(false);

        if (multiTenant) {
            String mtIdLookup = config.get("tenant-id-style").asString().orElse(DEFAULT_TENANT_ID_STYLE);
            switch (mtIdLookup) {
            case DEFAULT_TENANT_ID_STYLE:
                return new HostHeaderTenantId();
            case "token-handler":
                return new TokenHandlerTenantId(TokenHandler.create(config.get("tenant-id-handler")));
            case "domain":
                return new DomainTenantId(config.get("tenant-id-domain-level").asInt().orElse(3));
            case "none":
                return new NoTenantId();
            default:
                throw new IllegalArgumentException("Invalid configuration of multi tenancy id style. Type "
                                                           + mtIdLookup + " is not supported");
            }
        } else {
            return new NoTenantId();
        }
    }

    private static class NoTenantId implements TenantIdFinder {
        @Override
        public Optional<String> tenantId(ProviderRequest providerRequest) {
            return DEFAULT_TENANT_ID_VALUE;
        }
    }

    private static class HostHeaderTenantId implements TenantIdFinder {
        @Override
        public Optional<String> tenantId(ProviderRequest providerRequest) {
            List<String> hostList = providerRequest.env().headers().get("host");
            if (hostList == null || hostList.isEmpty()) {
                return Optional.empty();
            }
            String host = hostList.get(0);
            int index = host.indexOf(':');
            if (index == -1) {
                return Optional.of(host);
            }
            return Optional.of(host.substring(0, index));
        }
    }

    private static class TokenHandlerTenantId implements TenantIdFinder {
        private final TokenHandler tokenHandler;

        TokenHandlerTenantId(TokenHandler tokenHandler) {
            this.tokenHandler = tokenHandler;
        }

        @Override
        public Optional<String> tenantId(ProviderRequest providerRequest) {
            return tokenHandler.extractToken(providerRequest.env().headers());
        }
    }

    private static class DomainTenantId implements TenantIdFinder {
        private final HostHeaderTenantId host = new HostHeaderTenantId();
        private final int level;

        private DomainTenantId(int level) {
            this.level = level;
        }

        @Override
        public Optional<String> tenantId(ProviderRequest providerRequest) {
            return host.tenantId(providerRequest)
                    .flatMap(host -> {
                        // this should be host name including domain, otherwise we cannot identify
                        String[] split = host.split("\\.");
                        /*
                          tenant.oracle.com
                          level 1 is .com, level 2 is oracle
                          split[0] = tenant
                          split[1] = oracle
                          split[2] = com
                          requested level: 3 (expecting tenant)
                          split.length - level = 3 - 3 = 0
                          requested level: 1 (expecting com)
                          split.length - level = 3 - 1 = 2
                         */

                        int index = split.length - level;
                        if (index >= 0 && index < split.length) {
                            return Optional.of(split[index]);
                        }
                        return Optional.empty();
                    });
        }
    }
}
