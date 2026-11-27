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
package io.helidon.data.sql.datasource.ucp;

import java.util.List;
import java.util.Set;

import io.helidon.data.sql.datasource.DataSourceConfig;
import io.helidon.service.registry.Qualifier;

import oracle.ucp.jdbc.PoolDataSource;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class UcpDataSourceProviderServiceTest {
    @Test
    void defersConstructionUntilTheRegistrationIsSelected() throws Exception {
        UcpDataSourceConfig unusedConfig = mock(UcpDataSourceConfig.class);
        var constructionFailure = new IllegalStateException("The unused UCP source could not be constructed.");
        // The factory reads this option before creating a pool, which makes premature construction observable.
        when(unusedConfig.xaDataSource()).thenThrow(constructionFailure);
        var unused = DataSourceConfig.builder().name("unused").provider(unusedConfig).build();
        var orders = DataSourceConfig.builder().name("orders")
                .provider(UcpDataSourceConfig.builder().name("orders").url("jdbc:oracle:thin:@orders").build())
                .build();
        var registrations = new UcpDataSourceProviderService(() -> List.of(unused, orders)).services();
        assertThat(registrations.stream().map(registration -> registration.qualifiers()).toList(),
                   is(List.of(Set.of(Qualifier.createNamed("unused")), Set.of(Qualifier.createNamed("orders")))));
        var selected = (PoolDataSource) registrations.get(1).get();
        assertThat(selected.getURL(), is("jdbc:oracle:thin:@orders"));
        verifyZeroInteractions(unusedConfig);
        assertThat(assertThrows(IllegalStateException.class, registrations.getFirst()::get), sameInstance(constructionFailure));
        assertThat(registrations.get(1).get(), sameInstance(selected));
    }
}
