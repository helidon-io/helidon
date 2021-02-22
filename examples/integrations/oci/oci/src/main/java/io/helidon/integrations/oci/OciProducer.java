/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import io.helidon.config.ConfigValue;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;

/**
 * Class OciProducer.
 */
@ApplicationScoped
public class OciProducer {
    private static final String OCI_NAME_PREFIX = "oci";

    private AuthenticationDetailsProvider provider;
    private ClientConfiguration clientConfig;

    /**
     * Creates and sets up the {@link AuthenticationDetailsProvider} and {@link ClientConfiguration}.
     *
     * @param config injected from the container.
     */
    @Inject
    public OciProducer(io.helidon.config.Config config) {
        ConfigValue<Oci> configValue = config.get(OCI_NAME_PREFIX).as(Oci::create);
        if (configValue.isPresent()) {
            provider = configValue.get().provider();
            clientConfig = configValue.get().clientConfig();
        } else {
            throw new OciException("OCI cannot be properly configured!");
        }
    }

    /**
     * Produces {@link AuthenticationDetailsProvider}.
     *
     * @return provider.
     */
    @Produces
    public AuthenticationDetailsProvider getProvider() {
        return provider;
    }

    /**
     * Produces {@link ClientConfiguration}.
     *
     * @return clientConfig.
     */
    @Produces
    public ClientConfiguration getClientConfig() {
        return clientConfig;
    }
}
