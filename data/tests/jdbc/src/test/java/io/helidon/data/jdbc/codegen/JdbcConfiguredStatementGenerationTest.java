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
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcConfiguredStatementGenerationTest {

    @Test
    void validatesAnnotationSelectionAndDefersDeploymentChecks() {
        for (String annotation : List.of("@Jdbc.Statement", "@Jdbc.Statement(\" \")",
                "@Jdbc.Statement(name = \" \")", "@Jdbc.Statement(value = \"select 1\", name = \"find\")",
                "@Jdbc.Statement(value = \" \u0020\", name = \"find\")")) {
            TestCompiler.Result result = compile("Invalid.java", """
                    package example;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.Jdbc;
                    @Data.Repository @Data.Provider("jdbc")
                    interface Invalid {
                        %s
                        String find();
                    }
                    """.formatted(annotation));
            assertThat(String.join("\n", result.diagnostics()), result.success(), is(false));
        }
        TestCompiler.Result valid = compile("Valid.java", """
                package example;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                @Data.Repository @Data.Provider("jdbc")
                interface Valid {
                    @Jdbc.Statement(name = "deployment-only")
                    String find(String unknownUntilDeployment);
                    @Jdbc.Statement("select 1")
                    String literal();
                    @Jdbc.Statement(value = "", name = "keys") @Jdbc.GeneratedKeys("ID")
                    java.util.Optional<Long> insert(String value, String data__value, String statement,
                            String jdbcClient, String SQL_INSERT, String result, String rows);
                }
                """);
        assertThat(String.join("\n", valid.diagnostics()), valid.success(), is(true));
    }

    @Test
    void rejectsAvailableUnsupportedResultAndControlShapes() {
        for (String declaration : List.of("java.util.stream.Stream<String> find();",
                "void find(java.util.function.Consumer<java.util.stream.Stream<String>> callback);",
                "String find(java.util.Iterator<String> values);")) {
            TestCompiler.Result result = compile("Invalid.java", """
                    package example;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.Jdbc;
                    @Data.Repository @Data.Provider("jdbc")
                    interface Invalid {
                        @Jdbc.Statement(name = "configured")
                        %s
                    }
                    """.formatted(declaration));
            assertThat(String.join("\n", result.diagnostics()), result.success(), is(false));
        }
    }

    @Test
    void resolvesConfiguredContractsFromSeparatelyCompiledParents() {
        TestCompiler.Result parent = TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addOption("-parameters")
                .addClasspath(List.of(RuntimeType.class, JdbcClient.class))
                .addSource("Parent.java", """
                        package example;
                        import io.helidon.data.jdbc.Jdbc;
                        public interface Parent<T> {
                            @Jdbc.Statement(name = "count") @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
                            int count();
                            @Jdbc.Statement(name = "projection")
                            T projection(String name);
                        }
                        """).build().compile();
        assertThat(String.join("\n", parent.diagnostics()), parent.success(), is(true));
        TestCompiler.Result child = TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(RuntimeType.class, Data.class, JdbcClient.class, Service.class, Tx.class,
                        Generated.class, TypeName.class, DataGeneratorProvider.class, RepositoryCodegenProvider.class,
                        JdbcRepositoryGeneratorProvider.class))
                .addClasspathEntry(parent.classOutput())
                .addSource("Child.java", """
                        package example;
                        import io.helidon.data.Data;
                        @Data.Repository @Data.Provider("jdbc")
                        public interface Child extends Parent<String> {
                        }
                        """).build().compile();
        assertThat(String.join("\n", child.diagnostics()), child.success(), is(true));
    }

    @Test
    void rejectsConflictingInheritedLogicalInputNames() {
        TestCompiler.Result result = compile("Child.java", """
                package example;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                interface Left {
                    @Jdbc.Statement(name = "find") String find(String left);
                }
                interface Right {
                    @Jdbc.Statement(name = "find") String find(String right);
                }
                @Data.Repository @Data.Provider("jdbc")
                interface Child extends Left, Right {
                }
                """);
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(false));
        assertThat(String.join("\n", result.diagnostics()), containsString("incompatible parameter names"));
    }

    /**
     * A compiler placeholder cannot establish the deployment marker contract.
     */
    @Test
    void rejectsConfiguredInputsFromParentsWithoutParameterNames() {
        TestCompiler.Result result = compileInherited("""
                public interface Parent<T> {
                    @Jdbc.Statement(name = "projection")
                    T projection(String name);
                }
                """, false, "extends Parent<String> {}");
        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("parameter 1"));
        assertThat(diagnostics, containsString("projection"));
        assertThat(diagnostics, containsString("-parameters"));
        assertThat(diagnostics, containsString("redeclare"));
    }

    /**
     * Erased declaration descriptors distinguish generic and overloaded compiled methods.
     */
    @Test
    void resolvesRetainedNamesAcrossGenericAndOverloadedDeclarations() {
        TestCompiler.Result result = compileInherited("""
                public interface Parent<T> {
                    @Jdbc.Statement(name = "generic")
                    T find(T name);
                    @Jdbc.Statement(name = "binary")
                    String find(byte[] bytes);
                    @Jdbc.Statement(name = "primitive")
                    String find(boolean enabled, byte tiny, short small, int count, long id, float ratio, double value);
                    @Jdbc.Statement(name = "rename")
                    void rename(String name);
                    @Jdbc.Statement(name = "count") @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
                    int count(long id);
                }
                """, true, "extends Parent<String> {}");
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    /**
     * The spelling arg0 is valid when it comes from source or retained metadata.
     */
    @Test
    void acceptsDeclaredArg0NamesInSourceAndCompiledParents() {
        TestCompiler.Result inherited = compileInherited("""
                public interface Parent<T> {
                    @Jdbc.Statement(name = "find")
                    String find(String arg0);
                }
                """, true, "extends Parent<String> {}");
        assertThat(String.join("\n", inherited.diagnostics()), inherited.success(), is(true));
        TestCompiler.Result source = compile("SourceNames.java", """
                package example;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                @Data.Repository @Data.Provider("jdbc")
                interface SourceNames {
                    @Jdbc.Statement(name = "find")
                    String find(String arg0);
                }
                """);
        assertThat(String.join("\n", source.diagnostics()), source.success(), is(true));
    }

    /**
     * A child can restore the declared binding name without rebuilding its parent.
     */
    @Test
    void acceptsSourceRedeclarationWhenParentNamesAreUnavailable() {
        TestCompiler.Result result = compileInherited("""
                public interface Parent<T> {
                    @Jdbc.Statement(name = "find")
                    T find(String name);
                }
                """, false, """
                extends Parent<String> {
                    @Override @Jdbc.Statement(name = "find")
                    String find(String name);
                }
                """);
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    /**
     * Parameterless configured selection and positional literals do not require names.
     */
    @Test
    void acceptsParameterlessConfiguredAndPositionalLiteralParentsWithoutNames() {
        TestCompiler.Result result = compileInherited("""
                public interface Parent<T> {
                    @Jdbc.Statement(name = "count")
                    String count();
                    @Jdbc.Statement("SELECT ?")
                    T find(String name);
                }
                """, false, "extends Parent<String> {}");
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    /**
     * Nested declaring interfaces retain names in their own class files.
     */
    @Test
    void resolvesRetainedNamesFromNestedDeclaringInterfaces() {
        TestCompiler.Result result = compileInherited("""
                public interface Parent<T> {
                    interface Nested {
                        @Jdbc.Statement(name = "find")
                        String find(String name);
                    }
                }
                """, true, "extends Parent.Nested {}");
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    private static TestCompiler.Result compileInherited(String parentBody, boolean preserveNames, String childDeclaration) {
        TestCompiler.Builder parentCompiler = TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addClasspath(List.of(RuntimeType.class, JdbcClient.class))
                .addSource("Parent.java", """
                        package example;
                        import io.helidon.data.jdbc.Jdbc;
                        %s
                        """.formatted(parentBody));
        if (preserveNames) {
            parentCompiler.addOption("-parameters");
        }
        TestCompiler.Result parent = parentCompiler.build().compile();
        assertThat(String.join("\n", parent.diagnostics()), parent.success(), is(true));
        return TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(RuntimeType.class, Data.class, JdbcClient.class, Service.class, Tx.class,
                        Generated.class, TypeName.class, DataGeneratorProvider.class, RepositoryCodegenProvider.class,
                        JdbcRepositoryGeneratorProvider.class))
                .addClasspathEntry(parent.classOutput())
                .addSource("Child.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        public interface Child %s
                        """.formatted(childDeclaration))
                .build().compile();
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
