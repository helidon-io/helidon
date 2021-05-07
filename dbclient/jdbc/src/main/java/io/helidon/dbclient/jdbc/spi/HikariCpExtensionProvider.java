/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import io.helidon.config.Config;
import io.helidon.dbclient.jdbc.HikariCpExtension;

/**
 * Java Service loader interface that provides JDBC DB Client configuration extension.
 */
public interface HikariCpExtensionProvider {
    /**
     * Configuration key of the extension provider.
     * @return configuration key expected under {@code connection.helidon}
     */
    String configKey();

    /**
     * Get instance of JDBC DB Client configuration extension.
     *
     * @param config configuration of this provider to obtain an extension instance
     * @return JDBC DB Client configuration extension
     */
    HikariCpExtension extension(Config config);
}
