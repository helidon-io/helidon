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
package io.helidon.data.jdbc.codegen;

import java.util.List;

import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.data.codegen.DataGeneratorProvider;
import io.helidon.data.codegen.common.RepositoryCodegenProvider;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

final class JdbcCodegenTestSupport {
    private JdbcCodegenTestSupport() {
    }

    /**
     * Creates the annotation-processing compiler used by JDBC code-generation tests.
     *
     * @return compiler builder with all repository processors and contracts
     */
    static TestCompiler.Builder compiler() {
        return TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(load("io.helidon.data.Data"),
                                      load("io.helidon.data.jdbc.JdbcClient"),
                                      load("io.helidon.service.registry.Service"),
                                      load("io.helidon.transaction.Tx"),
                                      load("io.helidon.common.Generated"),
                                      load("io.helidon.common.types.TypeName"),
                                      DataGeneratorProvider.class,
                                      RepositoryCodegenProvider.class,
                                      JdbcPersistenceGeneratorProvider.class));
    }

    /**
     * Compiles an invalid repository and checks the attached diagnostic.
     *
     * @param fileName source file name
     * @param source repository source
     * @param expectedDiagnostic diagnostic fragment
     */
    static void assertCompilationFailure(String fileName, String source, String expectedDiagnostic) {
        TestCompiler.Result result = compiler()
                .addSource(fileName, source)
                .build()
                .compile();
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(false));
        assertThat(result.diagnostics(), hasItem(containsString(expectedDiagnostic)));
    }

    /**
     * Counts non-overlapping occurrences of one generated-source fragment.
     *
     * @param source generated source
     * @param fragment fragment to count
     * @return occurrence count
     */
    static int occurrences(String source, String fragment) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(fragment, index)) >= 0) {
            count++;
            index += fragment.length();
        }
        return count;
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Missing test classpath entry " + className, e);
        }
    }
}
