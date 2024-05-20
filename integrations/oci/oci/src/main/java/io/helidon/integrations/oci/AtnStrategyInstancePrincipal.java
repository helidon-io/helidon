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

package io.helidon.integrations.oci;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.spi.OciAtnStrategy;
import io.helidon.service.registry.Service;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;

/**
 * Instance principal authentication strategy, uses the
 * {@link com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 40)
@Service.Provider
class AtnStrategyInstancePrincipal implements OciAtnStrategy {
    static final String STRATEGY = "instance-principal";

    // we do not use the constant, as it is marked as internal, and we only need the IP address anyway
    // see com.oracle.bmc.auth.AbstractFederationClientAuthenticationDetailsProviderBuilder.METADATA_SERVICE_BASE_URL
    private static final String IMDS_ADDRESS = "169.254.169.254";
    private static final System.Logger LOGGER = System.getLogger(AtnStrategyInstancePrincipal.class.getName());

    private final LazyValue<Optional<AbstractAuthenticationDetailsProvider>> provider;

    AtnStrategyInstancePrincipal(OciConfig config) {
        provider = createProvider(config);
    }

    @Override
    public String strategy() {
        return STRATEGY;
    }

    @Override
    public Optional<AbstractAuthenticationDetailsProvider> provider() {
        return provider.get();
    }

    private static LazyValue<Optional<AbstractAuthenticationDetailsProvider>> createProvider(OciConfig config) {
        return LazyValue.create(() -> {
            if (imdsAvailable(config)) {
                var builder = InstancePrincipalsAuthenticationDetailsProvider.builder()
                        .timeoutForEachRetry((int) config.atnTimeout().toMillis());

                config.imdsBaseUri()
                        .map(URI::toString)
                        .ifPresent(builder::metadataBaseUrl);

                return Optional.of(builder.build());
            }
            return Optional.empty();
        });
    }

    private static boolean imdsAvailable(OciConfig config) {
        Duration timeout = config.imdsTimeout();

        try {
            if (InetAddress.getByName(config.imdsBaseUri().map(URI::getHost).orElse(IMDS_ADDRESS))
                    .isReachable((int) timeout.toMillis())) {
                return RegionProviderSdk.regionFromImds() != null;
            }
            return false;
        } catch (IOException e) {
            LOGGER.log(Level.TRACE,
                       "imds service is not reachable, or timed out for address: " + IMDS_ADDRESS + ", instance principal "
                               + "strategy is not available.",
                       e);
            return false;
        }
    }
}
