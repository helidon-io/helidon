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

package io.helidon.integrations.vault.secrets.database;

import java.util.LinkedList;
import java.util.List;

import io.helidon.common.config.Config;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.Engine;
import io.helidon.integrations.vault.spi.SecretsEngineProvider;

/**
 * Service for {@link DbSecrets#ENGINE}.
 */
public class DbEngineProvider implements SecretsEngineProvider<DbSecrets>,
                                         io.helidon.integrations.vault.spi.InjectionProvider {

    private static final List<InjectionType<?>> INJECTABLES;

    static {
        List<InjectionType<?>> injectables = new LinkedList<>();

        injectables.add(InjectionType.create(DbSecrets.class,
                (vault, config, instanceConfig) -> instanceConfig.vaultPath()
                                                                 .map(it -> vault.secrets(DbSecrets.ENGINE, it))
                                                                 .orElseGet(() -> vault.secrets(DbSecrets.ENGINE))));

        INJECTABLES = List.copyOf(injectables);
    }

    @Override
    public Engine<DbSecrets> supportedEngine() {
        return DbSecrets.ENGINE;
    }

    @Override
    public DbSecrets createSecrets(Config config, RestApi restApi, String mount) {
        return new DbSecretsImpl(restApi, mount);
    }

    @Override
    public List<InjectionType<?>> injectables() {
        return INJECTABLES;
    }
}
