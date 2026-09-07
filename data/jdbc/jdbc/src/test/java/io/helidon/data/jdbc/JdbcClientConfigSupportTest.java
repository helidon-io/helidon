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
package io.helidon.data.jdbc;

import java.util.Optional;

import javax.sql.DataSource;

import io.helidon.data.DataException;
import io.helidon.data.sql.common.ConnectionConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcClientConfigSupportTest {

    @Test
    void distinguishesMissingAndConflictingRuntimeConnectionSources() {
        JdbcClientConfig missing = config(Optional.empty(), Optional.empty(), Optional.empty());
        JdbcClientConfig conflicting = config(Optional.of(mock(ConnectionConfig.class)),
                                              Optional.of("inventory-source"),
                                              Optional.of(mock(DataSource.class)));

        DataException missingFailure = assertThrows(DataException.class,
                                                    () -> JdbcClientConfigSupport.validate(missing));
        DataException conflictingFailure = assertThrows(DataException.class,
                                                        () -> JdbcClientConfigSupport.validate(conflicting));

        assertThat(missingFailure.getMessage(),
                   is("The JDBC client configuration does not define a connection source. "
                              + "Configure exactly one using connection properties, a data source name, "
                              + "or a DataSource instance."));
        assertThat(conflictingFailure.getMessage(),
                   is("The JDBC client configuration defines multiple connection sources. "
                              + "Configure exactly one using connection properties, a data source name, "
                              + "or a DataSource instance."));
    }

    private static JdbcClientConfig config(Optional<ConnectionConfig> connection,
                                           Optional<String> dataSourceName,
                                           Optional<DataSource> dataSource) {
        JdbcClientConfig config = mock(JdbcClientConfig.class);
        when(config.name()).thenReturn("inventory");
        when(config.connection()).thenReturn(connection);
        when(config.dataSourceName()).thenReturn(dataSourceName);
        when(config.dataSource()).thenReturn(dataSource);
        return config;
    }
}
