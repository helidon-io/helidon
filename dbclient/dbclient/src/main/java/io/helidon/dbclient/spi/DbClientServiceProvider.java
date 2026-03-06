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
package io.helidon.dbclient.spi;

import java.util.Collection;

import io.helidon.common.DeprecationSupport;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClientService;

/**
 * Java service loader service to configure client services.
 */
public interface DbClientServiceProvider {

    /**
     * The configuration key expected in config.
     * If the key exists, the builder looks into
     * {@code global}, {@code named}, and {@code typed} sub keys
     * to configure appropriate instances.
     * Method {@link #create(Config)} is called for each
     * configuration as follows:
     * <ul>
     *     <li>{@code global}: the configuration key is used to get a new instance</li>
     *     <li>{code named}: for each configuration node with a list of nodes, a new instance is requested</li>
     *     <li>{code typed}: for each configuration node with a list of types, a new instance is requested</li>
     * </ul>
     * @return name of the configuration key (such as "tracing")
     */
    String configKey();

    /**
     * Create a new interceptor instance with the configuration provided.
     *
     * @param config configuration node with additional properties that are (maybe) configured for this interceptor
     * @return an interceptor to handle DB statements
     * @deprecated use {@link #create(io.helidon.config.Config)} instead
     */
    @SuppressWarnings("removal")
    @Deprecated(since = "4.4.0", forRemoval = true)
    default Collection<DbClientService> create(io.helidon.common.config.Config config) {
        // default to avoid forcing deprecated symbols references
        return create(Config.config(config));
    }

    /**
     * Create a new interceptor instance with the configuration provided.
     * <p>
     * API Note: the default method implementation is provided for backward compatibility
     * and <b>will be removed in the next major version</b>
     *
     * @param config configuration node with additional properties that are (maybe) configured for this interceptor
     * @return an interceptor to handle DB statements
     * @since 4.4.0
     */
    @SuppressWarnings("removal")
    default Collection<DbClientService> create(Config config) {
        // default to preserve backward compatibility
        // require the deprecated variant to be implemented
        DeprecationSupport.requireOverride(this, DbClientServiceProvider.class, "create",
                io.helidon.common.config.Config.class);
        return create((io.helidon.common.config.Config) config);
    }

}
