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
package io.helidon.data.jdbc.tests;

import java.nio.file.Files;
import java.nio.file.Path;

import io.helidon.data.jdbc.JdbcClient;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneratedRepositoryTest {

    /**
     * Proves generated {@code int} update methods preserve overflow instead of
     * silently narrowing the JDBC long update count.
     */
    @Test
    void rejectsAnUpdateCountOutsideThePrimitiveIntRange() {
        JdbcClient client = mock(JdbcClient.class);
        JdbcClient.Statement statement = mock(JdbcClient.Statement.class);
        when(client.create(anyString())).thenReturn(statement);
        when(statement.bind(anyInt(), any())).thenReturn(statement);
        when(statement.execute()).thenReturn(Long.MAX_VALUE);
        OverflowRepository repository = new OverflowRepository__Jdbc(client);

        assertThrows(ArithmeticException.class, () -> repository.rename("overflow", 1));
    }

    /**
     * Proves application mapper names cannot become Java keywords or replace
     * constructor parameters owned by JDBC client selection.
     *
     * @throws Exception when the compiler output cannot be inspected
     */
    @Test
    void generatesCollisionSafeMapperDependencyNames() throws Exception {
        String source = generatedSource(MapperNameCollisionRepository.class);

        assertThat(source, containsString("private final JdbcClient.RowMapper<String> classRowMapper;"));
        assertThat(source, containsString("private final JdbcClient.RowMapper<String> namedJdbcClientRowMapper;"));
        assertThat(source, containsString("private final JdbcClient.RowMapper<String> explicitRowMapper;"));
        assertThat(source, containsString("MapperNameCollisionRepository.ExplicitRowMapper explicitRowMapper"));
        assertThat(source, not(containsString("explicitRowMapperRowMapper")));
        assertThat(source, containsString("JdbcClient.RowMapper<MapperNameCollisionRepository.Box<String>> "
                                                  + "boxRowMapper;"));
        assertThat(source, containsString("JdbcClient.RowMapper<MapperNameCollisionRepository.Box<Integer>> "
                                                  + "boxRowMapper2;"));
        assertThat(source, containsString("@Service.Named(\"mapper-name-collision\")"));
        assertThat(source, not(containsString("Optional<JdbcClient>")));
        assertThat(source, not(containsString("Supplier<JdbcClient>")));
        assertThat(source, not(containsString(".orElseGet(")));
    }

    /**
     * Proves implicit record mappers are shared only when their complete mapped types are equal.
     *
     * @throws Exception when the compiler output cannot be inspected
     */
    @Test
    void reusesRecordMappersByResolvedType() throws Exception {
        String source = generatedSource(RecordMapperReuseRepository.class);

        assertThat(source.split("RowMapper<RecordMapperReuseRepository.Projection<String>> MAPPER_", -1).length - 1,
                   is(1));
        assertThat(source.split("\\.map\\(MAPPER_STRING_VALUE\\)", -1).length - 1, is(2));
        assertThat(source, not(containsString("MAPPER_OPTIONAL_STRING_VALUE")));
        assertThat(source.split("RowMapper<RecordMapperReuseRepository.Projection<Integer>> MAPPER_", -1).length - 1,
                   is(1));
    }

    /**
     * Proves overload signatures and lossy constant normalization cannot produce duplicate generated fields,
     * including when one overload is inherited. Compilation of the fixture independently proves the emitted
     * identifiers are valid Java.
     *
     * @throws Exception when the compiler output cannot be inspected
     */
    @Test
    void generatesCollisionSafeMethodFieldNames() throws Exception {
        String source = generatedSource(GeneratedNameCollisionRepository.class);

        assertThat(source, containsString("SQL_FIND_VALUE = \"SELECT ?\""));
        assertThat(source, containsString("SQL_FIND_VALUE_1 = \"SELECT ?\""));
        assertThat(source, containsString("SQL_FIND_VALUE_2 = \"SELECT ?\""));
        assertThat(source, containsString("SQL_INHERITED_VALUE = \"SELECT ?\""));
        assertThat(source, containsString("SQL_INHERITED_VALUE_1 = \"SELECT ?\""));
    }

    /**
     * Proves a repository requesting more than ten key columns emits one
     * fluent {@code addColumn} call per name and no size-limited collection
     * construction in generated source.
     *
     * @throws Exception when the compiler output cannot be inspected
     */
    @Test
    void emitsEveryWideGeneratedKeyColumnAsAFluentCall() throws Exception {
        String source = generatedSource(WideGeneratedKeyRepository.class);

        assertThat(source.split("\\.addColumn\\(", -1).length - 1, is(11));
        for (int index = 1; index <= 11; index++) {
            assertThat(source, containsString(".addColumn(\"KEY_%02d\")".formatted(index)));
        }
        assertThat(source, not(containsString("List.of(")));
        assertThat(source, not(containsString("new String[]")));
    }

    /**
     * Reads source emitted by annotation processing for a repository fixture.
     *
     * @param repositoryType repository interface
     * @return generated repository source
     * @throws Exception when the compiler output cannot be located or read
     */
    private static String generatedSource(Class<?> repositoryType) throws Exception {
        Path testClasses = Path.of(GeneratedRepositoryTest.class.getProtectionDomain()
                                           .getCodeSource()
                                           .getLocation()
                                           .toURI());
        Path generatedSource = testClasses.getParent()
                .resolve("generated-sources/annotations")
                .resolve(repositoryType.getName().replace('.', '/') + "__Jdbc.java");
        return Files.readString(generatedSource);
    }
}
