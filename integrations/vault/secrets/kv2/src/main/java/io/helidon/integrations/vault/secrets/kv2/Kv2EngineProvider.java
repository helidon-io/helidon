/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.vault.secrets.kv2;

import java.util.LinkedList;
import java.util.List;

import io.helidon.common.config.Config;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.Engine;
import io.helidon.integrations.vault.spi.InjectionProvider;
import io.helidon.integrations.vault.spi.SecretsEngineProvider;

/**
 * Java Service Loader implementation of Vault KV version 2 secrets engine.
 */
public class Kv2EngineProvider implements SecretsEngineProvider<Kv2Secrets>,
                                          InjectionProvider {
    private static final List<InjectionType<?>> INJECTABLES;

    static {
        List<InjectionType<?>> injectables = new LinkedList<>();

        injectables.add(InjectionType.create(Kv2Secrets.class,
                                             (vault, config, instanceConfig) -> instanceConfig.vaultPath()
                                                     .map(it -> vault.secrets(Kv2Secrets.ENGINE, it))
                                                     .orElseGet(() -> vault.secrets(Kv2Secrets.ENGINE))));

        INJECTABLES = List.copyOf(injectables);
    }

    @Override
    public Engine<Kv2Secrets> supportedEngine() {
        return Kv2Secrets.ENGINE;
    }

    @Override
    public Kv2Secrets createSecrets(Config config, RestApi restAccess, String mount) {
        return new Kv2SecretsImpl(restAccess, mount);
    }

    @Override
    public List<InjectionType<?>> injectables() {
        return INJECTABLES;
    }
}
