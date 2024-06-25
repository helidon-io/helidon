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

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.spi.OciAuthenticationMethod;
import io.helidon.service.registry.Service;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;

/**
 * Config file based authentication method, uses the {@link com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 20)
@Service.Provider
class AuthenticationMethodConfigFile implements OciAuthenticationMethod {
    static final String METHOD = "config-file";

    private final LazyValue<Optional<AbstractAuthenticationDetailsProvider>> provider;

    AuthenticationMethodConfigFile(Supplier<Optional<ConfigFileReader.ConfigFile>> configFile) {
        provider = createProvider(configFile);
    }

    @Override
    public String method() {
        return METHOD;
    }

    @Override
    public Optional<AbstractAuthenticationDetailsProvider> provider() {
        return provider.get();
    }

    private static LazyValue<Optional<AbstractAuthenticationDetailsProvider>>
    createProvider(Supplier<Optional<ConfigFileReader.ConfigFile>> configFileSupplier) {

        return LazyValue.create(() -> configFileSupplier.get()
                .map(ConfigFileAuthenticationDetailsProvider::new));
    }
}
