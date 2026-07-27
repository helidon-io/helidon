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

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.data.codegen.DataGeneratorProvider;
import io.helidon.data.codegen.common.RepositoryCodegenProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcCodegenValidationTest {

    @Test
    void infersFromJavaMethodShapeWithoutInspectingSqlText() throws IOException {
        TestCompiler.Result result = compiler()
                .addSource("ExecutionRepository.java", """
                        package example;

                        import java.util.List;
                        import java.util.Optional;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;

                        record ValueRecord(long id, String name) {
                        }

                        @Data.Repository
                        @Data.Provider("jdbc")
                        interface ExecutionRepository {
                            @Jdbc.Statement("/* UPDATE DELETE */ with value_cte as (select 1) "
                                    + "select 'INSERT' as value")
                            String absentExecution();

                            @Jdbc.Statement("delete update insert are only text in this query contract")
                            @Jdbc.Execution(Jdbc.ExecutionType.AUTO)
                            Optional<String> explicitAuto();

                            @Jdbc.Statement("not parsed as SQL")
                            List<ValueRecord> records();

                            @Jdbc.Statement("select count(*) from VALUE")
                            @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
                            long primitiveQuery();

                            @Jdbc.Statement("select misleading from VALUE")
                            void inferredUpdate();
                        }
                        """)
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String source = Files.readString(result.sourceOutput().resolve("example/ExecutionRepository__Jdbc.java"));
        assertThat(source, containsString("return jdbcStatement.map(String.class).one();"));
        assertThat(source, containsString("return jdbcStatement.map(String.class).optional();"));
        assertThat(source, containsString("return jdbcStatement.map(long.class).one();"));
        assertThat(source, containsString("jdbcStatement.execute();"));
    }

    @Test
    void generatesDefaultNamedRequiredAndFallbackPersistenceUnitInjection() throws IOException {
        TestCompiler.Result result = compiler()
                .addSource("PersistenceUnitRepositories.java", """
                        package example;

                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;

                        @Data.Repository
                        @Data.Provider("jdbc")
                        interface DefaultRepository {
                            @Jdbc.Statement("select 1")
                            Integer value();
                        }

                        @Data.Repository
                        @Data.Provider("jdbc")
                        @Data.PersistenceUnit("inventory")
                        interface RequiredRepository {
                            @Jdbc.Statement("select 1")
                            Integer value();
                        }

                        @Data.Repository
                        @Data.Provider("jdbc")
                        @Data.PersistenceUnit(value = "optional", required = false)
                        interface FallbackRepository {
                            @Jdbc.Statement("select 1")
                            Integer value();
                        }
                        """)
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String defaultSource = generated(result, "DefaultRepository");
        String requiredSource = generated(result, "RequiredRepository");
        String fallbackSource = generated(result, "FallbackRepository");
        assertThat(defaultSource, containsString("@Service.Named(\"@default\")"));
        assertThat(requiredSource, containsString("@Service.Named(\"inventory\")"));
        assertThat(fallbackSource, containsString("Optional<JdbcClient> namedJdbcClient"));
        assertThat(fallbackSource, containsString("Supplier<JdbcClient> jdbcClient"));
        assertThat(fallbackSource, containsString("namedJdbcClient.orElseGet(jdbcClient)"));
    }

    @Test
    void acceptsACompatibleInheritedGenericMapperContract() {
        TestCompiler.Result result = compiler()
                .addSource("InheritedMapperRepository.java", """
                        package example;

                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        import io.helidon.data.jdbc.JdbcClient;
                        import io.helidon.service.registry.Service;

                        record Value(String text) {
                        }

                        abstract class BaseMapper<T> implements JdbcClient.RowMapper<T> {
                        }

                        @Service.Singleton
                        final class ValueMapper extends BaseMapper<Value> {
                            @Override
                            public Value map(JdbcClient.Row row) {
                                return new Value(row.required(1, String.class));
                            }
                        }

                        @Data.Repository
                        @Data.Provider("jdbc")
                        interface InheritedMapperRepository {
                            @Jdbc.Statement("select VALUE from TEST_VALUE")
                            @Jdbc.RowMapper(ValueMapper.class)
                            Value value();
                        }
                        """)
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    @Test
    void rejectsInvalidStatementsBindingsAndRepositoryShapes() {
        assertFailure("MissingStatementRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        @Data.Repository @Data.Provider("jdbc")
                        interface MissingStatementRepository {
                            String find();
                        }
                        """,
                      "requires @Jdbc.Statement");
        assertFailure("EntityJdbcRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        @Data.Repository @Data.Provider("jdbc")
                        interface EntityJdbcRepository extends Data.CrudRepository<String, Long> {
                        }
                        """,
                      "must not extend entity-oriented repository interfaces");
        assertFailure("UnknownMarkerRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        interface UnknownMarkerRepository {
                            @Jdbc.Statement("select VALUE from TEST_VALUE where ID = :other")
                            String find(long id);
                        }
                        """,
                      "has no matching repository parameter");
        assertFailure("DottedMarkerRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        interface DottedMarkerRepository {
                            @Jdbc.Statement("select VALUE from TEST_VALUE where ID = :filter.id")
                            String find(long filter);
                        }
                        """,
                      "Dotted named parameters are not supported");
        assertFailure("UnusedParameterRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        interface UnusedParameterRepository {
                            @Jdbc.Statement("select VALUE from TEST_VALUE where ID = :id")
                            String find(long id, String state);
                        }
                        """,
                      "Repository parameter is not used by SQL: state");
        assertFailure("OneMarkerTwoParametersRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        interface OneMarkerTwoParametersRepository {
                            @Jdbc.Statement("select VALUE from TEST_VALUE where ID = ?")
                            String find(long id, String state);
                        }
                        """,
                      "marker count 1 does not match repository parameter count 2");
    }

    @Test
    void rejectsUnsupportedParametersAndResultModels() {
        assertFailure("OptionalParameterRepository.java", repositoryWithParameter("java.util.Optional<String>"),
                      "Unsupported declarative SQL parameter type");
        assertFailure("CollectionParameterRepository.java", repositoryWithParameter("java.util.List<String>"),
                      "Unsupported declarative SQL parameter type");
        assertFailure("ArrayParameterRepository.java", repositoryWithParameter("String[]"),
                      "Unsupported declarative SQL parameter type");
        assertFailure("RecordParameterRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        record Filter(long id) {}
                        @Data.Repository @Data.Provider("jdbc")
                        interface RecordParameterRepository {
                            @Jdbc.Statement("select VALUE from TEST_VALUE where ID = :value")
                            String find(Filter value);
                        }
                        """,
                      "Unsupported declarative SQL parameter type");
        assertFailure("NestedRecordRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        record Nested(String value) {}
                        record Result(Nested nested) {}
                        @Data.Repository @Data.Provider("jdbc")
                        interface NestedRecordRepository {
                            @Jdbc.Statement("select VALUE as nested from TEST_VALUE")
                            Result find();
                        }
                        """,
                      "Unsupported record component type");
        assertFailure("CollectionRecordRepository.java", """
                        package example;
                        import java.util.List;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        record Result(List<String> values) {}
                        @Data.Repository @Data.Provider("jdbc")
                        interface CollectionRecordRepository {
                            @Jdbc.Statement("select VALUE as values from TEST_VALUE")
                            Result find();
                        }
                        """,
                      "Unsupported record component type");
        assertFailure("MutableResultRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        final class MutableResult {}
                        @Data.Repository @Data.Provider("jdbc")
                        interface MutableResultRepository {
                            @Jdbc.Statement("select VALUE from TEST_VALUE")
                            MutableResult find();
                        }
                        """,
                      "must be a record or declare @Jdbc.RowMapper");
    }

    @Test
    void rejectsInvalidExplicitMapperServicesAndInheritedMismatch() {
        assertFailure("InterfaceMapperRepository.java", mapperRepository("""
                        interface ValueMapper extends JdbcClient.RowMapper<Value> {
                        }
                        """, "ValueMapper"),
                      "Mapper must be a concrete class");
        assertFailure("AbstractMapperRepository.java", mapperRepository("""
                        abstract class ValueMapper implements JdbcClient.RowMapper<Value> {
                        }
                        """, "ValueMapper"),
                      "Mapper must be a concrete class");
        assertFailure("NestedMapperRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        import io.helidon.data.jdbc.JdbcClient;
                        record Value(String text) {}
                        final class Container {
                            final class ValueMapper implements JdbcClient.RowMapper<Value> {
                                public Value map(JdbcClient.Row row) {
                                    return new Value(row.required(1, String.class));
                                }
                            }
                        }
                        @Data.Repository @Data.Provider("jdbc")
                        interface NestedMapperRepository {
                            @Jdbc.Statement("select VALUE from TEST_VALUE")
                            @Jdbc.RowMapper(Container.ValueMapper.class)
                            Value find();
                        }
                        """,
                      "Mapper must not be a non-static nested class");
        assertFailure("InheritedMismatchRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        import io.helidon.data.jdbc.JdbcClient;
                        record Value(String text) {}
                        abstract class BaseMapper<T> implements JdbcClient.RowMapper<T> {}
                        final class WrongMapper extends BaseMapper<String> {
                            public String map(JdbcClient.Row row) {
                                return row.required(1, String.class);
                            }
                        }
                        @Data.Repository @Data.Provider("jdbc")
                        interface InheritedMismatchRepository {
                            @Jdbc.Statement("select VALUE from TEST_VALUE")
                            @Jdbc.RowMapper(WrongMapper.class)
                            Value find();
                        }
                        """,
                      "Mapper must implement JdbcClient.RowMapper<example.Value>");
        assertFailure("InaccessibleMapperContainer.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        import io.helidon.data.jdbc.JdbcClient;
                        public final class InaccessibleMapperContainer {
                            private static final class Hidden {
                                public record Value(String text) {}
                                public static final class ValueMapper implements JdbcClient.RowMapper<Value> {
                                    public Value map(JdbcClient.Row row) {
                                        return new Value(row.required(1, String.class));
                                    }
                                }
                                @Data.Repository @Data.Provider("jdbc")
                                interface Repository {
                                    @Jdbc.Statement("select VALUE from TEST_VALUE")
                                    @Jdbc.RowMapper(ValueMapper.class)
                                    Value find();
                                }
                            }
                        }
                        """,
                      "Mapper is not accessible to generated code");
    }

    @Test
    void rejectsARecordWithAnInaccessibleEnclosingDeclaration() {
        assertFailure("InaccessibleRecordContainer.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        public final class InaccessibleRecordContainer {
                            private static final class Hidden {
                                public record Value(String text) {}
                                @Data.Repository @Data.Provider("jdbc")
                                interface Repository {
                                    @Jdbc.Statement("select VALUE as text from TEST_VALUE")
                                    Value find();
                                }
                            }
                        }
                        """,
                      "Record type is not accessible to generated code");
    }

    private static String repositoryWithParameter(String parameterType) {
        return """
                package example;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                @Data.Repository @Data.Provider("jdbc")
                interface ParameterRepository {
                    @Jdbc.Statement("select VALUE from TEST_VALUE where VALUE = :value")
                    String find(%s value);
                }
                """.formatted(parameterType);
    }

    private static String mapperRepository(String mapperDeclaration, String mapperType) {
        return """
                package example;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                import io.helidon.data.jdbc.JdbcClient;
                record Value(String text) {}
                %s
                @Data.Repository @Data.Provider("jdbc")
                interface MapperRepository {
                    @Jdbc.Statement("select VALUE from TEST_VALUE")
                    @Jdbc.RowMapper(%s.class)
                    Value find();
                }
                """.formatted(mapperDeclaration, mapperType);
    }

    private static TestCompiler.Builder compiler() {
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

    private static void assertFailure(String fileName, String source, String expectedDiagnostic) {
        TestCompiler.Result result = compiler()
                .addSource(fileName, source)
                .build()
                .compile();
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(false));
        assertThat(result.diagnostics(), hasItem(containsString(expectedDiagnostic)));
    }

    private static String generated(TestCompiler.Result result, String repository) throws IOException {
        return Files.readString(result.sourceOutput().resolve("example/" + repository + "__Jdbc.java"));
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Missing test classpath entry " + className, e);
        }
    }
}
