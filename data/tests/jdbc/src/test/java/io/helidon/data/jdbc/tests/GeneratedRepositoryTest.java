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
    @SuppressWarnings("helidon:api:internal")
    void rejectsAnUpdateCountOutsideThePrimitiveIntRange() {
        JdbcClient client = mock(JdbcClient.class);
        JdbcClient.Statement statement = mock(JdbcClient.Statement.class);
        when(client.create(anyString(), anyInt())).thenReturn(statement);
        when(statement.bind(anyInt(), any())).thenReturn(statement);
        when(statement.execute()).thenReturn(Long.MAX_VALUE);
        OverflowRepository repository = new OverflowRepository__Jdbc(client);

        assertThrows(ArithmeticException.class, () -> repository.rename("overflow", 1));
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
        Path testClasses = Path.of(GeneratedRepositoryTest.class.getProtectionDomain()
                                           .getCodeSource()
                                           .getLocation()
                                           .toURI());
        Path generatedSource = testClasses.getParent()
                .resolve("generated-sources/annotations/io/helidon/data/jdbc/tests/"
                                 + "WideGeneratedKeyRepository__Jdbc.java");

        String source = Files.readString(generatedSource);

        assertThat(source.split("\\.addColumn\\(", -1).length - 1, is(11));
        for (int index = 1; index <= 11; index++) {
            assertThat(source, containsString(".addColumn(\"KEY_%02d\")".formatted(index)));
        }
        assertThat(source, not(containsString("List.of(")));
        assertThat(source, not(containsString("new String[]")));
    }
}
