/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.hikari.spi;

import java.util.ServiceLoader;

import io.helidon.common.config.Config;
import io.helidon.dbclient.hikari.HikariMetricsRegistry;

/**
 * Java {@link ServiceLoader} interface that provides implementations of {@link HikariMetricsRegistry}.
 */
public interface HikariMetricsProvider {

    /**
     * Configuration key of the extension provider.
     *
     * @return configuration key expected under {@code connection.helidon}
     */
    String configKey();

    /**
     * Get instance of {@link HikariMetricsRegistry} from config.
     *
     * @param config provider configuration
     * @return interceptor to handle connection pool configuration.
     */
    HikariMetricsRegistry extension(Config config);

}
