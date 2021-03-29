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

package io.helidon.security.spi;

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.reactive.Single;
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
     */
    Supplier<Single<Optional<String>>> secret(Config config);

    /**
     * Create secret supplier from configuration object.
     *
     * @param providerConfig configuration of a specific secret
     * @return supplier to retrieve the secret
     */
    Supplier<Single<Optional<String>>> secret(T providerConfig);
}
