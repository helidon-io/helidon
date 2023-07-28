/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.jdbc.spi;

import java.util.Collections;
import java.util.List;

import io.helidon.common.config.Config;
import io.helidon.common.config.ConfiguredProvider;
import io.helidon.dbclient.jdbc.JdbcConnectionPool;

/**
 * {@link java.util.ServiceLoader} provider interface for JDBC connection pool.
 * This interface serves as
 *
 * @param <P> JDBC connection pool implementation type
 */
public interface JdbcConnectionPoolProvider<P extends JdbcConnectionPool>
        extends ConfiguredProvider<P> {

    /**
     * Create a new instance from the configuration located on the provided node.
     * List of connection pool configuration interceptor providers from service loader
     * is passed to the new instance.
     *
     * @param config located at {@link #configKey()} node
     * @param name name of the configured implementation
     * @param extensions connection pool configuration interceptor providers
     *
     * @return a new instance created from this config node
     */
    P create(Config config, String name, List<JdbcCpExtensionProvider> extensions);

    @Override
    @SuppressWarnings("unchecked")
    default P create(Config config, String name) {
        return (P) create(config, name, Collections.EMPTY_LIST);
    }

}
