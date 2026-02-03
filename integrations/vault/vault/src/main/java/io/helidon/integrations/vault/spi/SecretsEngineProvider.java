/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

package io.helidon.integrations.vault.spi;

import io.helidon.common.DeprecationSupport;
import io.helidon.config.Config;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.Engine;
import io.helidon.integrations.vault.Secrets;

/**
 * A Java Service Loader SPI to support additional secret engines of Vault.
 *
 * @param <T> type of the secrets supported by this provider
 */
public interface SecretsEngineProvider<T extends Secrets> {
    /**
     * Supported engine by this provider.
     *
     * @return engine that is supported, used to choose the correct provider for an engine
     * @see io.helidon.integrations.vault.Vault#secrets(io.helidon.integrations.vault.Engine)
     */
    Engine<T> supportedEngine();

    /**
     * Create a secrets instance to provide API to access this engine.
     *
     * @param config configuration that can be used to customize the engine
     * @param restAccess to access REST API of the vault, preconfigured with token
     * @param mount mount point of this engine's secrets
     *
     * @return a new secrets instance to be used to access secrets
     * @deprecated use {@link #createSecrets(io.helidon.config.Config, io.helidon.integrations.common.rest.RestApi, String)}
     * instead
     */
    @SuppressWarnings("removal")
    @Deprecated(since = "4.4.0", forRemoval = true)
    default T createSecrets(io.helidon.common.config.Config config, RestApi restAccess, String mount) {
        // default to avoid forcing deprecated symbols references
        return createSecrets(Config.config(config), restAccess, mount);
    }

    /**
     * Create a secrets instance to provide API to access this engine.
     * <p>
     * API Note: the default method implementation is provided for backward compatibility
     * and <b>will be removed in the next major version</b>
     *
     * @param config     configuration that can be used to customize the engine
     * @param restAccess to access REST API of the vault, preconfigured with token
     * @param mount      mount point of this engine's secrets
     * @return a new secrets instance to be used to access secrets\
     * @since 4.4.0
     */
    default T createSecrets(Config config, RestApi restAccess, String mount) {
        // default to preserve backward compatibility
        // require the deprecated variant to be implemented
        DeprecationSupport.requireOverride(this, SecretsEngineProvider.class, "createSecrets",
                io.helidon.common.config.Config.class,
                RestApi.class,
                String.class);
        return createSecrets((io.helidon.common.config.Config) config, restAccess, mount);
    }
}
