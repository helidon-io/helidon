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
import io.helidon.integrations.vault.SysApi;

/**
 * Java Service Loader service to add support for sys APIs.
 *
 * @param <T> type of API supported
 */
public interface SysProvider<T> {
    /**
     * Supported API by this provider.
     * @return sys API supported
     */
    SysApi<T> supportedApi();

    /**
     * Create a new instance of Sys.
     *
     * @param config vault configuration
     * @param restAccess access to REST endpoints
     * @return an API to access sys functions
     */
    @SuppressWarnings("removal")
    @Deprecated(since = "4.4.0", forRemoval = true)
    default T createSys(io.helidon.common.config.Config config, RestApi restAccess) {
        // default to avoid forcing deprecated symbols references
        return createSys(Config.config(config), restAccess);
    }

    /**
     * Create a new instance of Sys.
     * <p>
     * API Note: the default method implementation is provided for backward compatibility
     * and <b>will be removed in the next major version</b>
     *
     * @param config     vault configuration
     * @param restAccess access to REST endpoints
     * @return an API to access sys functions
     * @since 4.4.0
     */
    @SuppressWarnings("removal")
    default T createSys(Config config, RestApi restAccess) {
        // default to preserve backward compatibility
        // require the deprecated variant to be implemented
        DeprecationSupport.requireOverride(this, SysProvider.class, "createSys",
                io.helidon.common.config.Config.class,
                RestApi.class,
                String.class);
        return createSys((io.helidon.common.config.Config) config, restAccess);
    }
}
