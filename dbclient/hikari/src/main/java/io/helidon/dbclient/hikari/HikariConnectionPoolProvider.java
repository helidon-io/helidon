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
package io.helidon.dbclient.hikari;

import io.helidon.common.config.Config;
import io.helidon.dbclient.jdbc.JdbcConnectionPool;
import io.helidon.dbclient.jdbc.spi.JdbcConnectionPoolProvider;

/**
 * Hikari connection pool {@link java.util.ServiceLoader} provider.
 */
public class HikariConnectionPoolProvider implements JdbcConnectionPoolProvider {

    private static final String  CONFIG_NAME = "hikari";

    /**
     * Creates an instance of Hikari connection pool {@link java.util.ServiceLoader} provider.
     */
    public HikariConnectionPoolProvider() {
    }

    @Override
    public String configKey() {
        return CONFIG_NAME;
    }

    @Override
    public JdbcConnectionPool create(Config config, String name) {
        return HikariConnectionPool.builder()
                .config(config)
                .serviceName(name)
                .build();
    }

}
