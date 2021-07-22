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

package io.helidon.integrations.vault.spi;

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
     * @param restAccess access to REST endpoits
     * @return a API to access sys functions
     */
    T createSys(Config config, RestApi restAccess);
}
