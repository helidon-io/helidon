/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
     */
    SecureUserStore create(Config config);
}
