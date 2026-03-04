/*
 * Copyright (c) 2019, 2026 Oracle and/or its affiliates.
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
package io.helidon.security.providers.httpauth.spi;

import io.helidon.common.DeprecationSupport;
import io.helidon.config.Config;
import io.helidon.security.providers.httpauth.SecureUserStore;

/**
 * A service to supply custom implementation of {@link io.helidon.security.providers.httpauth.SecureUserStore}.
 */
public interface UserStoreService {
    /**
     * Configuration key of this store, expected under the security provider configuration.
     *
     * @return configuration key of this service
     */
    String configKey();

    /**
     * Create the secure user store to use with these providers.
     *
     * @param config configuration located on {@link #configKey()}
     * @return a user store to be used by providers
     * @deprecated use {@link #create(io.helidon.config.Config)} instead
     */
    @SuppressWarnings("removal")
    @Deprecated(since = "4.4.0", forRemoval = true)
    default SecureUserStore create(io.helidon.common.config.Config config) {
        // default to avoid forcing deprecated symbols references
        return create(Config.config(config));
    }

    /**
     * Create the secure user store to use with these providers.
     * <p>
     * API Note: the default method implementation is provided for backward compatibility
     * and <b>will be removed in the next major version</b>
     *
     * @param config configuration located on {@link #configKey()}
     * @return a user store to be used by providers
     * @since 4.4.0
     */
    @SuppressWarnings("removal")
    default SecureUserStore create(Config config) {
        // default to preserve backward compatibility
        // require the deprecated variant to be implemented
        DeprecationSupport.requireOverride(this, UserStoreService.class, "create", io.helidon.common.config.Config.class);
        return create((io.helidon.common.config.Config) config);
    }
}
