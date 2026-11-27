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
package io.helidon.data.sql.datasource.jdbc;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import io.helidon.data.sql.datasource.DataSourceConfig;
import io.helidon.service.registry.Qualifier;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcDataSourceServiceTest {
    @Test
    void defersDriverResolutionUntilTheRegistrationIsSelected() {
        // The URL has no registered driver. The failure should surface only when the registration is requested.
        var definition = DataSourceConfig.builder().name("unused")
                .provider(JdbcDataSourceConfig.builder().url("jdbc:helidon-test:missing-driver").build())
                .build();
        var registrations = new JdbcDataSourceService(() -> List.of(definition)).services();
        assertThat(registrations.stream().map(registration -> registration.qualifiers()).toList(),
                   is(List.of(Set.of(Qualifier.createNamed("unused")))));
        var failure = assertThrows(RuntimeException.class, registrations.getFirst()::get);
        assertThat(failure.getCause(), instanceOf(SQLException.class));
    }
}
