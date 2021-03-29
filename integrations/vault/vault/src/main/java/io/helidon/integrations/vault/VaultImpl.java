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

package io.helidon.integrations.vault;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.logging.Logger;

import io.helidon.common.LazyValue;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.spi.AuthMethodProvider;
import io.helidon.integrations.vault.spi.SecretsEngineProvider;
import io.helidon.integrations.vault.spi.SysProvider;

/**
 * Main entry point to Vault secrets.
 *
 * @see #secrets(Engine)
 * @see #secrets(Engine, String)
 */
class VaultImpl implements Vault {

    private static final Logger LOGGER = Logger.getLogger(VaultImpl.class.getName());

    private static final LazyValue<List<AuthMethodProvider<?>>> METHODS = LazyValue.create(() -> {
        List<AuthMethodProvider<?>> result = new LinkedList<>();
        HelidonServiceLoader.builder(ServiceLoader.load(AuthMethodProvider.class))
                .build()
                .forEach(result::add);

        LOGGER.fine(() -> "Available Authentication Method providers: " + result);

        return result;
    });

    private static final LazyValue<List<SecretsEngineProvider<?>>> ENGINES = LazyValue.create(() -> {
        List<SecretsEngineProvider<?>> result = new LinkedList<>();
        HelidonServiceLoader.builder(ServiceLoader.load(SecretsEngineProvider.class))
                .build()
                .forEach(result::add);

        LOGGER.fine(() -> "Available Secret Engine providers: " + result);

        return result;
    });

    private static final LazyValue<List<SysProvider<?>>> SYS = LazyValue.create(() -> {
        List<SysProvider<?>> result = new LinkedList<>();
        HelidonServiceLoader.builder(ServiceLoader.load(SysProvider.class))
                .build()
                .forEach(result::add);

        LOGGER.fine(() -> "Available Sys providers: " + result);

        return result;
    });

    private final Config config;
    private final RestApi restAccess;

    VaultImpl(Builder builder, RestApi restAccess) {
        this.config = builder.config();
        this.restAccess = restAccess;
    }

    @Override
    public <T extends Secrets> T secrets(Engine<T> engine) {
        return findEngineProvider(engine)
                .map(it -> it.createSecrets(config, restAccess, it.supportedEngine().defaultMount()))
                .orElseThrow(() -> new IllegalArgumentException("There is no provider available for engine "
                                                                        + engine.type()
                                                                        + engine.version().orElse("")));
    }

    @Override
    public <T extends Secrets> T secrets(Engine<T> engine, String mount) {
        return findEngineProvider(engine)
                .map(it -> it.createSecrets(config, restAccess, mount))
                .orElseThrow(() -> new IllegalArgumentException("There is no provider available for engine "
                                                                        + engine.type()
                                                                        + engine.version().orElse("")));
    }

    @Override
    public <T> T auth(AuthMethod<T> method) {
        return findMethodProvider(method)
                .map(it -> it.createAuth(config, restAccess, method.defaultPath()))
                .orElseThrow(() -> new IllegalArgumentException("There is no provider available for auth method "
                                                                        + method.type()));
    }

    @Override
    public <T> T auth(AuthMethod<T> method, String path) {
        return findMethodProvider(method)
                .map(it -> it.createAuth(config, restAccess, path))
                .orElseThrow(() -> new IllegalArgumentException("There is no provider available for auth method "
                                                                        + method.type()));
    }

    @Override
    public <T> T sys(SysApi<T> api) {
        return findSys(api)
                .map(it -> it.createSys(config, restAccess))
                .orElseThrow(() -> new IllegalArgumentException("There is no provider available for sys API: "
                                                                        + api.apiType().getName()));
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<SysProvider<T>> findSys(SysApi<T> sysApi) {
        for (SysProvider<?> sysProvider : SYS.get()) {
            SysApi<?> supportedApi = sysProvider.supportedApi();
            if (supportedApi.apiType().equals(sysApi.apiType())) {
                return Optional.of((SysProvider<T>) sysProvider);
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<AuthMethodProvider<T>> findMethodProvider(AuthMethod<T> method) {
        for (AuthMethodProvider<?> engineProvider : METHODS.get()) {
            AuthMethod<?> supportedMethod = engineProvider.supportedMethod();
            if (supportedMethod.type().equals(method.type())
                    && (method.apiType() == Object.class)
                    || method.apiType().equals(supportedMethod.apiType())) {
                return Optional.of((AuthMethodProvider<T>) engineProvider);
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private <T extends Secrets> Optional<SecretsEngineProvider<T>> findEngineProvider(Engine<T> engine) {
        for (SecretsEngineProvider<?> engineProvider : ENGINES.get()) {
            Engine<?> supportedEngine = engineProvider.supportedEngine();
            if (supportedEngine.version().equals(engine.version())
                    && supportedEngine.type().equals(engine.type())
                    && (engine.secretsType() == Secrets.class)
                    || engine.secretsType().equals(supportedEngine.secretsType())) {
                return Optional.of((SecretsEngineProvider<T>) engineProvider);
            }
        }
        return Optional.empty();
    }
}
