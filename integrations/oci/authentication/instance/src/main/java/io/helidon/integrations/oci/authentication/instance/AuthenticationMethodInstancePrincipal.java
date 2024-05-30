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

package io.helidon.integrations.oci.authentication.instance;

import java.net.URI;
import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.HelidonOci;
import io.helidon.integrations.oci.OciConfig;
import io.helidon.integrations.oci.spi.OciAtnMethod;
import io.helidon.service.registry.Service;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;

/**
 * Instance principal authentication method, uses the
 * {@link com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 40)
@Service.Provider
class AuthenticationMethodInstancePrincipal implements OciAtnMethod {
    static final String METHOD = "instance-principal";

    private final LazyValue<Optional<AbstractAuthenticationDetailsProvider>> provider;

    AuthenticationMethodInstancePrincipal(OciConfig config) {
        provider = createProvider(config);
    }

    @Override
    public String method() {
        return METHOD;
    }

    @Override
    public Optional<AbstractAuthenticationDetailsProvider> provider() {
        return provider.get();
    }

    private static LazyValue<Optional<AbstractAuthenticationDetailsProvider>> createProvider(OciConfig config) {
        return LazyValue.create(() -> {
            if (HelidonOci.imdsAvailable(config)) {
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

}
