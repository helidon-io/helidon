/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.OciConfig;
import io.helidon.service.registry.Service;

import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider.InstancePrincipalsAuthenticationDetailsProviderBuilder;

@Weight(Weighted.DEFAULT_WEIGHT)
@Service.Provider
class MockedAuthenticationMethodInstancePrincipal extends AuthenticationMethodInstancePrincipal {

    private static InstancePrincipalsAuthenticationDetailsProviderBuilder providerBuilder;

    MockedAuthenticationMethodInstancePrincipal(OciConfig config,
                                                Supplier<Optional<InstancePrincipalsAuthenticationDetailsProviderBuilder>>
                                                       builder) {
        super(config, builder);
        providerBuilder = builder.get().get();
    }

    static InstancePrincipalsAuthenticationDetailsProviderBuilder getBuilder() {
        return providerBuilder;
    }
}
