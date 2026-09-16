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
package io.helidon.data.jakarta.persistence.codegen;

import java.nio.file.Files;
import java.util.List;

import io.helidon.codegen.api.stability.ApiStabilityProcessor;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Api;
import io.helidon.common.types.TypeName;
import io.helidon.data.Data;
import io.helidon.data.codegen.DataGeneratorProvider;
import io.helidon.data.jakarta.persistence.JpaRepositoryExecutor;
import io.helidon.service.registry.Service;

import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;

@SuppressWarnings({Api.SUPPRESS_INTERNAL, Api.SUPPRESS_PREVIEW})
class GeneratedApiStabilityTest {

    @Test
    void generatedSourcesSuppressPreviewApiDiagnostics() {
        var result = compiler()
                .addSource("BookRepository.java", """
                        package example;

                        import io.helidon.data.Data;

                        @SuppressWarnings("helidon:api:preview")
                        @Data.Repository
                        interface BookRepository extends Data.CrudRepository<Book, Long> {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(result.diagnostics(), empty());
        for (String generatedFile : List.of("BookRepository__Jpa.java", "Book__EntityProvider.java")) {
            var generatedSource = result.sourceOutput().resolve("example").resolve(generatedFile);
            assertThat("Generated source " + generatedSource, Files.isRegularFile(generatedSource), is(true));
        }
    }

    @Test
    void handwrittenPreviewApiUseStillFailsCompilation() {
        var result = compiler()
                .addSource("BookRepository.java", """
                        package example;

                        import io.helidon.data.Data;

                        @Data.Repository
                        interface BookRepository extends Data.CrudRepository<Book, Long> {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(result.diagnostics(),
                   hasItem(allOf(containsString("error:"),
                                 containsString("/BookRepository.java:"),
                                 containsString("io.helidon.data.Data is preview API"))));
    }

    private static TestCompiler.Builder compiler() {
        return TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addProcessor(ApiStabilityProcessor::new)
                .addProcessor(AptProcessor::new)
                .addOption("-Ahelidon.api.preview=fail")
                .addClasspath(List.of(Api.class,
                                      TypeName.class,
                                      Data.class,
                                      DataGeneratorProvider.class,
                                      JpaRepositoryExecutor.class,
                                      EntityCodegenProvider.class,
                                      Service.class,
                                      Entity.class))
                .addSource("Book.java", """
                        package example;

                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;

                        @Entity
                        public class Book {
                            @Id
                            private Long id;

                            public Long id() {
                                return id;
                            }
                        }
                        """);
    }
}
