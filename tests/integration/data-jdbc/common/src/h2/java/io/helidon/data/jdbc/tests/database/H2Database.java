/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc.tests.database;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

/**
 * H2 fixture shared by imperative and declarative JDBC matrix tests.
 */
public final class H2Database {
    /**
     * JDBC URL used by H2 matrix tests and direct JDBC assertions.
     */
    public static final String URL = "jdbc:h2:mem:jdbc_data_jdbc_matrix;DB_CLOSE_DELAY=-1";

    private static final String GENERATED_KEY_COLUMN_PROPERTY = "helidon.data.jdbc.tests.generated-key-column";

    private H2Database() {
    }

    /**
     * Creates Helidon config for the H2 matrix database.
     *
     * @return config
     */
    public static Config config() {
        System.clearProperty(GENERATED_KEY_COLUMN_PROPERTY);
        return Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.connection.url", URL,
                "data.persistence-units.jdbc.0.connection.jdbc-driver-class-name", "org.h2.Driver",
                "data.persistence-units.jdbc.0.init-script.resource-path", "db/h2/schema-init.sql")));
    }
}
