/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.config.Config;
import io.helidon.dbclient.DbClientService;

/**
 * Java service loader service to configure client services.
 */
public interface DbClientServiceProvider {

    /**
     * The configuration key expected in config.
     * If the key exists, the builder looks into
     * {@code global}, {@code named}, and {@code typed} subkeys
     * to configure appropriate instances.
     * Method {@link #create(io.helidon.config.Config)} is called for each
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
     */
    Collection<DbClientService> create(Config config);

}
