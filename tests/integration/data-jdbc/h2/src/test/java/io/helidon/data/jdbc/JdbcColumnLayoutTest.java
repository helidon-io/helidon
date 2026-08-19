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

import io.helidon.data.DataException;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcColumnLayoutTest {
    private JdbcClient client;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:jdbc_column_layout;DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS POKEMON");
            statement.execute("CREATE TABLE POKEMON (ID BIGINT PRIMARY KEY, NAME VARCHAR(80), CATEGORY VARCHAR(80))");
            statement.execute("INSERT INTO POKEMON VALUES (1, 'Pikachu', 'Electric')");
        }
        client = new JdbcClientImpl(dataSource, JdbcConnectionLease.ownedProvider());
    }

    /**
     * Proves scalar column-one mapping ignores ambiguity in labels that the
     * mapping does not request.
     */
    @Test
    void mapsColumnOneScalarWhenUnusedLabelsAreDuplicates() {
        long id = client.create("""
                SELECT ID,
                       NAME AS detail,
                       CATEGORY AS DETAIL
                FROM POKEMON
                """)
                .map(Long.class)
                .one();

        assertThat(id, is(1L));
    }

    /**
     * Proves index-based row access remains valid when projected labels are
     * duplicates.
     */
    @Test
    void mapsByIndexWhenResultLabelsAreDuplicates() {
        IndexedPokemon pokemon = client.create("""
                SELECT ID AS duplicate_value,
                       NAME AS DUPLICATE_VALUE,
                       CATEGORY AS duplicate_value
                FROM POKEMON
                """)
                .map(row -> new IndexedPokemon(row.required(1, Long.class),
                                               row.required(2, String.class),
                                               row.required(3, String.class)))
                .one();

        assertThat(pokemon, is(new IndexedPokemon(1, "Pikachu", "Electric")));
    }

    /**
     * Proves a uniquely requested label remains resolvable alongside unrelated
     * duplicate labels.
     */
    @Test
    void resolvesUniqueLabelWhenOtherLabelsAreDuplicates() {
        long id = client.create("""
                SELECT ID AS pokemon_id,
                       NAME AS detail,
                       CATEGORY AS DETAIL
                FROM POKEMON
                """)
                .map(row -> row.required("POKEMON_ID", Long.class))
                .one();

        assertThat(id, is(1L));
    }

    /**
     * Proves requested labels that differ only by case are rejected as
     * ambiguous.
     */
    @Test
    void rejectsRequestedAmbiguousLabelCaseInsensitively() {
        DataException failure = assertThrows(DataException.class,
                                             () -> client.create("""
                                                     SELECT ID,
                                                            NAME AS detail,
                                                            CATEGORY AS DETAIL
                                                     FROM POKEMON
                                                     """)
                                                     .map(row -> row.required("DeTaIl", String.class))
                                                     .one());

        assertThat(failure.getMessage(), is("The result contains more than one column labeled 'DeTaIl'."));
    }

    /**
     * Proves a missing requested label produces a deterministic mapping
     * failure against real metadata.
     */
    @Test
    void rejectsMissingLabelDeterministically() {
        DataException failure = assertThrows(DataException.class,
                                             () -> client.create("SELECT ID AS pokemon_id FROM POKEMON")
                                                     .map(row -> row.required("name", String.class))
                                                     .one());

        assertThat(failure.getMessage(), is("The result does not contain a column labeled 'name'."));
    }

    /**
     * Projection used to prove that index-only mapping ignores labels.
     *
     * @param id identifier
     * @param name name
     * @param category category
     */
    private record IndexedPokemon(long id, String name, String category) {
    }
}
