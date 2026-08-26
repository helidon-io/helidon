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
package io.helidon.data.tests.mixed.provider;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class MixedProviderCodegenTest {

    /**
     * Verifies that every configured provider generates an unqualified repository.
     */
    @Test
    void generatesUnqualifiedRepositoryWithEveryProvider() throws Exception {
        assertThat(Files.isRegularFile(generatedSource(UnqualifiedRepository.class, "__Jdbc")), is(true));
        assertThat(Files.isRegularFile(generatedSource(UnqualifiedRepository.class, "__Jpa")), is(true));
    }

    /**
     * Verifies that an explicit JDBC provider prevents Jakarta Persistence generation.
     */
    @Test
    void generatesQualifiedJdbcRepositoryOnlyWithJdbc() throws Exception {
        assertThat(Files.isRegularFile(generatedSource(JdbcRepository.class, "__Jdbc")), is(true));
        assertThat(Files.exists(generatedSource(JdbcRepository.class, "__Jpa")), is(false));
    }

    /**
     * Verifies that an explicit Jakarta Persistence provider prevents JDBC generation.
     */
    @Test
    void generatesQualifiedJakartaRepositoryOnlyWithJakartaPersistence() throws Exception {
        assertThat(Files.isRegularFile(generatedSource(JakartaRepository.class, "__Jpa")), is(true));
        assertThat(Files.exists(generatedSource(JakartaRepository.class, "__Jdbc")), is(false));
    }

    private static Path generatedSource(Class<?> repositoryType, String suffix) throws Exception {
        Path testClasses = Path.of(MixedProviderCodegenTest.class.getProtectionDomain()
                                           .getCodeSource()
                                           .getLocation()
                                           .toURI());
        return testClasses.getParent()
                .resolve("generated-sources/annotations")
                .resolve(repositoryType.getName().replace('.', '/') + suffix + ".java");
    }
}
