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

import io.helidon.builder.api.RuntimeType;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.types.TypeName;
import io.helidon.data.Data;
import io.helidon.data.codegen.DataGeneratorProvider;
import io.helidon.data.codegen.common.RepositoryCodegenProvider;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.Service;
import io.helidon.transaction.Tx;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcCollectionReturnGenerationTest {

    private static final String QUERY_STATEMENT = "@Jdbc.Statement(\"select NAME from CONTACT\")";
    private static final String RETURN_DIAGNOSTIC =
            "The only supported collection return type is java.util.List<T>. Collection, Map, and Stream types "
                    + "are not supported as row types.";
    private static final String MAPPING_DIAGNOSTIC =
            "must be a supported scalar, be a record, or declare @Jdbc.RowMapper";
    private static final List<String> MAPPER_ANNOTATIONS = List.of("",
                                                                   "@Jdbc.RowMapper",
                                                                   "@Jdbc.RowMapper(ResultMapper.class)");

    /**
     * Proves exact {@code List<T>} remains supported with implicit, marker, and explicit mapping.
     */
    @Test
    void acceptsExactListResultForEveryMapperSelection() {
        for (String mapperAnnotation : MAPPER_ANNOTATIONS) {
            TestCompiler.Result result = compile("ListRepository.java", """
                    package example;

                    import java.util.List;
                    import java.util.Objects;

                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.Jdbc;
                    import io.helidon.data.jdbc.JdbcClient;

                    final class ResultMapper implements JdbcClient.RowMapper<String> {
                        @Override
                        public String map(JdbcClient.Row row) {
                            Objects.requireNonNull(row, "row");
                            return row.get(1, String.class);
                        }
                    }

                    @Data.Repository
                    @Data.Provider("jdbc")
                    interface ListRepository {
                        @Jdbc.Statement("select NAME from CONTACT")
                        %s
                        List<String> values();
                    }
                    """.formatted(mapperAnnotation));

            String diagnostics = String.join("\n", result.diagnostics());
            assertThat(mapperAnnotation + ": " + diagnostics, result.success(), is(true));
        }
    }

    /**
     * Proves a mapped application result may own detached collection data without becoming collection cardinality.
     */
    @Test
    void acceptsMappedApplicationResultContainingCollectionData() {
        for (String mapperAnnotation : MAPPER_ANNOTATIONS.subList(1, MAPPER_ANNOTATIONS.size())) {
            TestCompiler.Result result = compile("WrappedCollectionRepository.java", """
                    package example;

                    import java.util.List;
                    import java.util.Objects;

                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.Jdbc;
                    import io.helidon.data.jdbc.JdbcClient;

                    final class WrappedCollection {
                        private final List<String> values;

                        WrappedCollection(List<String> values) {
                            this.values = List.copyOf(values);
                        }
                    }

                    final class ResultMapper implements JdbcClient.RowMapper<WrappedCollection> {
                        @Override
                        public WrappedCollection map(JdbcClient.Row row) {
                            Objects.requireNonNull(row, "row");
                            return new WrappedCollection(List.of(row.get(1, String.class)));
                        }
                    }

                    @Data.Repository
                    @Data.Provider("jdbc")
                    interface WrappedCollectionRepository {
                        @Jdbc.Statement("select NAME from CONTACT")
                        %s
                        WrappedCollection value();
                    }
                    """.formatted(mapperAnnotation));

            String diagnostics = String.join("\n", result.diagnostics());
            assertThat(mapperAnnotation + ": " + diagnostics, result.success(), is(true));
        }
    }

    /**
     * Proves exact {@code java.util.Set<T>} is rejected independently of mapper selection.
     */
    @Test
    void rejectsExactSetResult() {
        assertRejected("SetRepository", "java.util.Set<String>");
    }

    /**
     * Proves exact {@code java.util.Map<K, V>} is rejected independently of mapper selection.
     */
    @Test
    void rejectsExactMapResult() {
        assertRejected("MapRepository", "java.util.Map<String, String>");
    }

    /**
     * Proves exact {@code java.util.stream.Stream<T>} is rejected independently of mapper selection.
     */
    @Test
    void rejectsExactStreamResult() {
        assertRejected("StreamRepository", "java.util.stream.Stream<String>");
    }

    /**
     * Proves every primitive stream interface is rejected independently of mapper selection.
     */
    @Test
    void rejectsPrimitiveStreamResults() {
        assertRejected("IntStreamRepository", "java.util.stream.IntStream");
        assertRejected("LongStreamRepository", "java.util.stream.LongStream");
        assertRejected("DoubleStreamRepository", "java.util.stream.DoubleStream");
    }

    /**
     * Proves the collection supertype is rejected independently of mapper selection.
     */
    @Test
    void rejectsCollectionResult() {
        assertRejected("CollectionRepository", "java.util.Collection<String>");
    }

    /**
     * Proves a concrete list implementation does not acquire list cardinality.
     */
    @Test
    void rejectsArrayListResult() {
        assertRejected("ArrayListRepository", "java.util.ArrayList<String>");
    }

    /**
     * Proves a concrete set implementation is rejected independently of mapper selection.
     */
    @Test
    void rejectsHashSetResult() {
        assertRejected("HashSetRepository", "java.util.HashSet<String>");
    }

    /**
     * Proves a concrete map implementation is rejected independently of mapper selection.
     */
    @Test
    void rejectsHashMapResult() {
        assertRejected("HashMapRepository", "java.util.HashMap<String, String>");
    }

    /**
     * Proves an application collection subtype cannot be disguised as a single mapped row.
     */
    @Test
    void rejectsApplicationCollectionSubtype() {
        assertRejected("CustomCollectionRepository", "CustomCollection", """
                final class CustomCollection extends java.util.ArrayList<String> {
                }
                """);
    }

    /**
     * Proves an application map subtype is rejected independently of mapper selection.
     */
    @Test
    void rejectsApplicationMapSubtype() {
        assertRejected("CustomMapRepository", "CustomMap", """
                final class CustomMap extends java.util.HashMap<String, String> {
                }
                """);
    }

    /**
     * Proves an application stream subtype is rejected independently of mapper selection.
     */
    @Test
    void rejectsApplicationStreamSubtype() {
        assertRejected("CustomStreamRepository", "CustomStream", """
                interface CustomStream extends java.util.stream.Stream<String> {
                }
                """);
    }

    /**
     * Proves an application subtype of the common stream base cannot be disguised as a single mapped row.
     */
    @Test
    void rejectsApplicationBaseStreamSubtype() {
        assertRejected("CustomBaseStreamRepository", "CustomBaseStream", """
                interface CustomBaseStream extends java.util.stream.BaseStream<String, CustomBaseStream> {
                }
                """);
    }

    /**
     * Proves the mapped row type inside exact list cardinality cannot itself be a collection.
     */
    @Test
    void rejectsNestedCollectionMappedType() {
        assertRejected("NestedCollectionRepository",
                       "java.util.List<java.util.Collection<String>>",
                       "java.util.Collection<String>",
                       "",
                       QUERY_STATEMENT);
    }

    /**
     * Proves the mapped row type inside optional cardinality cannot itself be a map.
     */
    @Test
    void rejectsOptionalMapMappedType() {
        assertRejected("OptionalMapRepository",
                       "java.util.Optional<java.util.HashMap<String, String>>",
                       "java.util.HashMap<String, String>",
                       "",
                       QUERY_STATEMENT);
    }

    /**
     * Proves explicit query selection does not bypass collection-shaped return validation.
     */
    @Test
    void rejectsCollectionResultWithExplicitQueryExecution() {
        assertRejected("ExplicitQueryCollectionRepository",
                       "java.util.Collection<String>",
                       "java.util.Collection<String>",
                       "",
                       """
                               @Jdbc.Statement("select NAME from CONTACT")
                               @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
                               """);
    }

    /**
     * Proves generated-key mapping applies the same collection-shaped return validation as queries.
     */
    @Test
    void rejectsGeneratedKeyCollectionResult() {
        assertRejected("GeneratedKeyCollectionRepository",
                       "java.util.Collection<String>",
                       "java.util.Collection<String>",
                       "",
                       """
                               @Jdbc.Statement("insert into CONTACT (NAME) values ('Bulbasaur')")
                               @Jdbc.GeneratedKeys
                               """);
    }

    private static void assertRejected(String repositoryName, String returnType) {
        assertRejected(repositoryName, returnType, returnType, "", QUERY_STATEMENT);
    }

    private static void assertRejected(String repositoryName, String returnType, String supportingType) {
        assertRejected(repositoryName, returnType, returnType, supportingType, QUERY_STATEMENT);
    }

    private static void assertRejected(String repositoryName,
                                       String returnType,
                                       String mappedType,
                                       String supportingType,
                                       String methodAnnotations) {
        for (String mapperAnnotation : MAPPER_ANNOTATIONS) {
            TestCompiler.Result result = compile(repositoryName + ".java", """
                    package example;

                    import java.util.Objects;

                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.Jdbc;
                    import io.helidon.data.jdbc.JdbcClient;

                    %s

                    final class ResultMapper implements JdbcClient.RowMapper<%s> {
                        @Override
                        public %s map(JdbcClient.Row row) {
                            Objects.requireNonNull(row, "row");
                            throw new UnsupportedOperationException();
                        }
                    }

                    @Data.Repository
                    @Data.Provider("jdbc")
                    interface %s {
                        %s
                        %s
                        %s values();
                    }
                    """.formatted(supportingType,
                                   mappedType,
                                   mappedType,
                                   repositoryName,
                                   methodAnnotations,
                                   mapperAnnotation,
                                   returnType));

            String diagnostics = String.join("\n", result.diagnostics());
            String scenario = mapperAnnotation.isEmpty() ? "without mapper" : "with " + mapperAnnotation;
            String reason = repositoryName + " " + scenario + ": " + diagnostics;
            assertThat(reason, result.success(), is(false));
            assertThat(reason, diagnostics, containsString("JDBC repositories do not support the return type"));
            assertThat(reason, diagnostics, containsString(RETURN_DIAGNOSTIC));
            assertThat(reason, diagnostics, not(containsString(MAPPING_DIAGNOSTIC)));
        }
    }

    private static TestCompiler.Result compile(String fileName, String source) {
        return TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(RuntimeType.class,
                                      Data.class,
                                      JdbcClient.class,
                                      Service.class,
                                      Tx.class,
                                      Generated.class,
                                      TypeName.class,
                                      DataGeneratorProvider.class,
                                      RepositoryCodegenProvider.class,
                                      JdbcRepositoryGeneratorProvider.class))
                .addSource(fileName, source)
                .build()
                .compile();
    }
}
