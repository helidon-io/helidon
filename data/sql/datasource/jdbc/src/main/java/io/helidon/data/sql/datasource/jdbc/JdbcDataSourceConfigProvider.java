/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.data.sql.datasource.jdbc;

import io.helidon.common.config.Config;
import io.helidon.data.sql.datasource.ProviderConfig;
import io.helidon.data.sql.datasource.spi.DataSourceConfigProvider;

/**
 * A {@link java.util.ServiceLoader} provider implementation of
 * {@link io.helidon.data.sql.datasource.spi.DataSourceConfigProvider} for a data source based on direct JDBC connections.
 */
public class JdbcDataSourceConfigProvider implements DataSourceConfigProvider {
    static final String PROVIDER_TYPE = "jdbc";

    /**
     * Required default constructor for {@link java.util.ServiceLoader}.
     */
    public JdbcDataSourceConfigProvider() {
    }

    @Override
    public String configKey() {
        return PROVIDER_TYPE;
    }

    @Override
    public ProviderConfig create(Config config, String name) {
        return JdbcDataSourceConfig.builder()
                .config(config)
                .name(name)
                .build();
    }
}
