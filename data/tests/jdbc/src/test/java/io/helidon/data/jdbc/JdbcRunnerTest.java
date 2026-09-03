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

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class JdbcRunnerTest {

    /**
     * Proves generated key names are snapshotted and duplicate validation uses
     * exact, case-sensitive equality.
     */
    @Test
    void validatesGeneratedColumnSnapshotWithExactEquality() {
        JdbcPreparationPlan plan = JdbcPreparationPlan.generatedKeys(List.of("ID", "id"));

        assertThat(plan.generatedColumns(), is(List.of("ID", "id")));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                        () -> JdbcPreparationPlan.generatedKeys(List.of("ID", "ID")));
        assertThat(failure.getMessage(), is("The generated column name 'ID' is duplicated."));
    }

    /**
     * Proves an imperative null bind is rejected before the datasource can be
     * accessed.
     */
    @Test
    void rejectsNullImperativeBindValueBeforeConnectionAcquisition() {
        DataSource dataSource = mock(DataSource.class);
        JdbcClientConfig config = JdbcClientConfig.builder()
                .dataSource("test-data-source")
                .buildPrototype();
        JdbcClient client = new JdbcClientImpl(config,
                                               dataSource,
                                               JdbcConnectionLease.ownedProvider(),
                                               JdbcClientConfigSupport.cachePolicy(config));

        NullPointerException nullValue = assertThrows(NullPointerException.class,
                                                      () -> client.create("INSERT INTO POKEMON(NAME) VALUES (?)")
                                                              .bind(1, null));

        assertThat(nullValue.getMessage(), is("The bind value must not be null."));
        verifyNoMoreInteractions(dataSource);
    }
}
