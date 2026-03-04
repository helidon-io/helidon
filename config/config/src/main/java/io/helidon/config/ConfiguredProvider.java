/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.config;

import io.helidon.common.DeprecationSupport;

/**
 * Providers that can be loaded from configuration should implement this interface.
 *
 * @param <T> type of the service this provider provides
 */
@SuppressWarnings("removal")
public interface ConfiguredProvider<T extends NamedService> extends io.helidon.common.config.ConfiguredProvider<T> {

    /**
     * {@inheritDoc}
     */
    @Override
    String configKey();

    /**
     * {@inheritDoc}
     *
     * @deprecated use {@link #create(Config, String)} instead
     */
    @Override
    @SuppressWarnings("removal")
    @Deprecated(since = "4.4.0", forRemoval = true)
    default T create(io.helidon.common.config.Config config, String name) {
        // default to avoid forcing deprecated symbols references
        return create(Config.config(config), name);
    }

    /**
     * Create a new instance from the configuration located
     * on the provided node.
     * <p>
     * API Note: the default method implementation is provided for backward compatibility
     * and <b>will be removed in the next major version</b>
     *
     * @param config located at {@link #configKey()} node
     * @param name   name of the configured implementation
     * @return a new instance created from this config node
     * @since 4.4.0
     */
    default T create(Config config, String name) {
        // default to preserve backward compatibility
        // require the deprecated variant to be implemented
        DeprecationSupport.requireOverride(this, ConfiguredProvider.class, "create", io.helidon.common.config.Config.class);
        return create((io.helidon.common.config.Config) config, name);
    }
}
