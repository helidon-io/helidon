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

package io.helidon.security.spi;

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.DeprecationSupport;
import io.helidon.config.Config;

/**
 * Provider that can retrieve secrets.
 *
 * @param <T> type of the custom configuration object
 */
public interface SecretsProvider<T extends ProviderConfig> extends SecurityProvider {
    /**
     * Create secret supplier from configuration.
     *
     * @param config config located on the node of the specific secret {@code config} node
     * @return supplier to retrieve the secret
     * @deprecated use {@link #secret(io.helidon.config.Config)} instead
     */
    @SuppressWarnings("removal")
    @Deprecated(since = "4.4.0", forRemoval = true)
    default Supplier<Optional<String>> secret(io.helidon.common.config.Config config) {
        // default to avoid forcing deprecated symbols references
        return secret(io.helidon.config.Config.config(config));
    }

    /**
     * Create secret supplier from configuration.
     * <p>
     * API Note: the default method implementation is provided for backward compatibility
     * and <b>will be removed in the next major version</b>
     *
     * @param config config located on the node of the specific secret {@code config} node
     * @return supplier to retrieve the secret
     * @since 4.4.0
     */
    @SuppressWarnings("removal")
    default Supplier<Optional<String>> secret(Config config) {
        // default to preserve backward compatibility
        // require the deprecated variant to be implemented
        DeprecationSupport.requireOverride(this, SecretsProvider.class, "secret", io.helidon.common.config.Config.class);
        return secret((io.helidon.common.config.Config) config);
    }

    /**
     * Create secret supplier from configuration object.
     *
     * @param providerConfig configuration of a specific secret
     * @return supplier to retrieve the secret
     */
    Supplier<Optional<String>> secret(T providerConfig);
}
