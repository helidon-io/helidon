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

package io.helidon.integrations.vault.secrets.transit;

import java.util.LinkedList;
import java.util.List;

import io.helidon.config.Config;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.Engine;
import io.helidon.integrations.vault.spi.InjectionProvider;
import io.helidon.integrations.vault.spi.SecretsEngineProvider;

/**
 * Java Service Loader service for Transit engine.
 */
public class TransitEngineProvider implements SecretsEngineProvider<TransitSecretsRx>,
                                              InjectionProvider {
    private static final List<InjectionType<?>> INJECTABLES;

    static {
        List<InjectionType<?>> injectables = new LinkedList<>();

        injectables.add(InjectionType.create(TransitSecretsRx.class,
                                             (vault, config, instanceConfig) -> instanceConfig.vaultPath()
                                                     .map(it -> vault.secrets(TransitSecretsRx.ENGINE, it))
                                                     .orElseGet(() -> vault.secrets(TransitSecretsRx.ENGINE))));

        injectables.add(InjectionType.create(TransitSecrets.class,
                                             (vault, config, instanceConfig) -> {
                                                 TransitSecretsRx rx = instanceConfig.vaultPath()
                                                         .map(it -> vault.secrets(TransitSecretsRx.ENGINE, it))
                                                         .orElseGet(() -> vault.secrets(TransitSecretsRx.ENGINE));

                                                 return new TransitSecretsImpl(rx);
                                             }));

        INJECTABLES = List.copyOf(injectables);
    }

    @Override
    public Engine<TransitSecretsRx> supportedEngine() {
        return TransitSecretsRx.ENGINE;
    }

    @Override
    public TransitSecretsRx createSecrets(Config config, RestApi restAccess, String mount) {
        return new TransitSecretsRxImpl(restAccess, mount);
    }

    @Override
    public List<InjectionType<?>> injectables() {
        return INJECTABLES;
    }
}
