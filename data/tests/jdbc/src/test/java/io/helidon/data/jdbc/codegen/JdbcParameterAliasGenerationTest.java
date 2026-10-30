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

class JdbcParameterAliasGenerationTest {

    /**
     * Compiles a generic library without parameter metadata, then compiles only the concrete child.
     * The child renames Java arguments and preserves aliases through omitted overrides.
     */
    @Test
    void inheritsAliasesWithoutParameterMetadata() throws IOException {
        TestCompiler.Result parent = compiler()
                .processors(List.of())
                .addSource("Parent.java", """
                        package example;
                        import java.util.List;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        public interface Parent<T> {
                            @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :key OR NAME = :key")
                            List<T> find(@Data.Param("key") T original);
                            @Jdbc.Statement("UPDATE CONTACT SET NAME = :name WHERE ID = :id")
                            @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
                            int update(@Data.Param("id") long original, @Data.Param("name") T text);
                            @Jdbc.Statement("SELECT nq'[ :ignored ]', $tag$:ignored$tag$, :𐐀name FROM CONTACT")
                            String lexical(@Data.Param("𐐀name") T original);
                        }
                        """)
                .build().compile();
        assertThat(parent.diagnostics().toString(), parent.success(), is(true));
        TestCompiler.Result child = compiler()
                .addClasspathEntry(parent.classOutput())
                .addSource("Child.java", """
                        package example;
                        import java.util.List;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        public interface Child extends Parent<String> {
                            @Override
                            @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :key OR NAME = :key")
                            List<String> find(String jdbcClient);
                        }
                        """)
                .build().compile();
        assertThat(child.diagnostics().toString(), child.success(), is(true));
        String source = Files.readString(child.sourceOutput().resolve("example/Child__Jdbc.java"));
        assertThat(source, containsString("SQL_FIND, 2)"));
        assertThat(Files.exists(child.classOutput().resolve("example/Child__Jdbc.class")), is(true));
    }

    /**
     * Resolves each parameter independently across diamonds and overrides at different depths.
     */
    @Test
    void resolvesDiamondsAndExplicitOverrides() {
        String parents = """
                interface Root<T> {
                    @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :key")
                    List<T> find(@Data.Param("key") T old);
                }
                interface Left extends Root<String> {
                    @Override @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :key")
                    List<String> find(String renamed);
                }
                interface Right {
                    @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :key")
                    List<String> find(@Data.Param("key") String another);
                }
                """;
        TestCompiler.Result same = compile(parents, "extends Left, Right", "");
        assertThat(same.diagnostics().toString(), same.success(), is(true));
        String conflict = parents.replace("@Data.Param(\"key\") String another", "@Data.Param(\"other\") String another");
        TestCompiler.Result invalid = compile(conflict, "extends Left, Right", "");
        assertThat(invalid.success(), is(false));
        assertThat(invalid.diagnostics().toString(), containsString("Declare an explicit @Data.Param"));
        TestCompiler.Result resolved = compile(conflict, "extends Left, Right", """
                @Override @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :chosen")
                List<String> find(@Data.Param("chosen") String statement);
                """);
        assertThat(resolved.diagnostics().toString(), resolved.success(), is(true));
    }

    /**
     * Rejects malformed aliases, ambiguous names, missing coverage, positional aliases, and controls.
     */
    @Test
    void rejectsInvalidAliasesAndCoverage() {
        for (String alias : List.of("", " ", ":key", "key ", "two words", "a.b", "1key", "key/*comment*/")) {
            TestCompiler.Result result = compile("", "", """
                    @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :key")
                    String find(@Data.Param("%s") String value);
                    """.formatted(alias));
            assertThat(alias + result.diagnostics(), result.success(), is(false));
            assertThat(result.diagnostics().toString(), containsString("complete marker name"));
        }
        for (String declaration : List.of(
                "@Jdbc.Statement(\"SELECT :key\") String find(@Data.Param(\"key\") String value, String key);",
                "@Jdbc.Statement(\"SELECT :Key\") String find(@Data.Param(\"key\") String value);",
                "@Jdbc.Statement(\"SELECT :other\") String find(@Data.Param(\"key\") String value);",
                "@Jdbc.Statement(\"SELECT :key\") String find(@Data.Param(\"key\") String value, String unused);",
                "@Jdbc.Statement(\"SELECT ?\") String find(@Data.Param(\"key\") String value);",
                "@Jdbc.Statement(\"SELECT :key\") String find(@Data.Param(\"key\") Consumer<String> value);")) {
            TestCompiler.Result result = compile("", "", declaration);
            assertThat(result.diagnostics().toString(), result.success(), is(false));
        }
    }

    /**
     * Compiles aliases that collide with generated fields, locals, and lambda parameters.
     */
    @Test
    void compilesGeneratedNameCollisionsAndLexicalRegions() {
        TestCompiler.Result result = compile("", "", """
                @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :SQL_FIND OR NAME = :jdbcClient")
                List<String> find(@Data.Param("SQL_FIND") String jdbcClient,
                                           @Data.Param("jdbcClient") String SQL_FIND);
                @Jdbc.Statement("INSERT INTO CONTACT(NAME) VALUES (:rows)") @Jdbc.GeneratedKeys("id")
                Optional<Long> insert(@Data.Param("rows") String value);
                @Jdbc.Statement("SELECT nq'[ :ignored ]', $tag$:ignored$tag$, :𐐀name FROM CONTACT")
                String lexical(@Data.Param("𐐀name") String result);
                @Data.Query("SELECT :javaName")
                default String unchangedQuery(@Data.Param(":invalid alias ignored") String javaName) {
                    return javaName;
                }
                """);
        assertThat(result.diagnostics().toString(), result.success(), is(true));
        TestCompiler.Result nested = compile("", "", """
                @Jdbc.Statement("SELECT /* outer /* nested */ */ :key")
                String find(@Data.Param("key") String value);
                """);
        assertThat(nested.success(), is(false));
        assertThat(nested.diagnostics().toString(), containsString("offset"));
    }

    /**
     * Replaces one inherited alias while preserving another alias and method type parameters and checked exceptions.
     */
    @Test
    void preservesPartialOverridesAndGenericMethodContracts() {
        TestCompiler.Result result = compile("""
                interface Parent {
                    @Jdbc.Statement("UPDATE CONTACT SET NAME = :old WHERE ID = :id")
                    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
                    <E extends Exception> int change(@Data.Param("old") String before,
                                                    @Data.Param("id") long key) throws E;
                }
                """, "extends Parent", """
                @Override
                @Jdbc.Statement("UPDATE CONTACT SET NAME = :replacement WHERE ID = :id")
                @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
                <E extends Exception> int change(@Data.Param("replacement") String result, long statement) throws E;
                """);
        assertThat(result.diagnostics().toString(), result.success(), is(true));
    }

    /**
     * Reports a remedy when a compiled parent has neither an alias nor retained Java names.
     */
    @Test
    void diagnosesMissingNamesInACompiledParent() {
        TestCompiler.Result parent = compiler().processors(List.of())
                .addSource("Parent.java", """
                        package example;
                        import io.helidon.data.jdbc.Jdbc;
                        public interface Parent {
                            @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :key")
                            String find(String key);
                        }
                        """).build().compile();
        assertThat(parent.diagnostics().toString(), parent.success(), is(true));
        TestCompiler.Result child = compiler().addClasspathEntry(parent.classOutput())
                .addSource("Child.java", """
                        package example;
                        import io.helidon.data.Data;
                        @Data.Repository @Data.Provider("jdbc")
                        public interface Child extends Parent {
                        }
                        """).build().compile();
        assertThat(child.success(), is(false));
        assertThat(child.diagnostics().toString(), containsString("Declare @Data.Param, use positional SQL"));
    }

    /**
     * Detects conflicting aliases from compiled parents and accepts an explicit child resolution.
     */
    @Test
    void resolvesConflictsAcrossSeparateCompilation() {
        TestCompiler.Result parents = compiler().processors(List.of())
                .addSource("Parents.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        interface Left<T> {
                            @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :key")
                            String find(@Data.Param("key") T original);
                        }
                        interface Right {
                            @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :key")
                            String find(@Data.Param("other") String another);
                        }
                        """).build().compile();
        assertThat(parents.diagnostics().toString(), parents.success(), is(true));
        for (boolean resolve : List.of(false, true)) {
            String override = resolve ? """
                    @Override @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :chosen")
                    String find(@Data.Param("chosen") String jdbcClient);
                    """ : "";
            TestCompiler.Result child = compiler().addClasspathEntry(parents.classOutput())
                    .addSource("Child.java", """
                            package example;
                            import io.helidon.data.Data;
                            import io.helidon.data.jdbc.Jdbc;
                            @Data.Repository @Data.Provider("jdbc")
                            public interface Child extends Left<String>, Right {
                                %s
                            }
                            """.formatted(override)).build().compile();
            assertThat(child.diagnostics().toString(), child.success(), is(resolve));
            if (!resolve) {
                assertThat(child.diagnostics().toString(), containsString("Inherited @Data.Param aliases conflict"));
            }
        }
    }

    /**
     * Preserves a covariant child signature while inheriting identical or conflicting aliases from generic parents.
     * The parents also compile separately without Java parameter metadata.
     */
    @Test
    void preservesCovariantOverridesWhileResolvingAliases() {
        for (boolean separate : List.of(false, true)) {
            for (String rightAlias : List.of("key", "other")) {
                String parents = """
                        package example;
                        import java.io.Serializable;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        interface Left<T> {
                            @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :key")
                            CharSequence find(@Data.Param("key") T value);
                        }
                        interface Right<T> {
                            @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :key")
                            Serializable find(@Data.Param("%s") T value);
                        }
                        interface Middle extends Right<String> {
                        }
                        """.formatted(rightAlias);
                TestCompiler.Result library = compiler().processors(List.of())
                        .addSource("Parents.java", parents).build().compile();
                assertThat(library.diagnostics().toString(), library.success(), is(true));
                for (boolean explicit : List.of(false, true)) {
                    TestCompiler.Builder compilation = compiler().addSource("Child.java", """
                            package example;
                            import io.helidon.data.Data;
                            import io.helidon.data.jdbc.Jdbc;
                            @Data.Repository @Data.Provider("jdbc")
                            public interface Child extends Left<String>, Middle {
                                @Override @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :key")
                                String find(%s String renamed);
                            }
                            """.formatted(explicit ? "@Data.Param(\"key\")" : ""));
                    if (separate) {
                        compilation.addClasspathEntry(library.classOutput());
                    } else {
                        compilation.addSource("Parents.java", parents);
                    }
                    TestCompiler.Result result = compilation.build().compile();
                    boolean valid = explicit || rightAlias.equals("key");
                    assertThat("Separate compilation: " + separate + ". " + result.diagnostics(), result.success(), is(valid));
                    if (valid) {
                        assertThat(Files.exists(result.classOutput().resolve("example/Child__Jdbc.class")), is(true));
                    } else {
                        assertThat(result.diagnostics().toString(), containsString("Inherited @Data.Param aliases conflict"));
                    }
                }
            }
        }
    }

    private static TestCompiler.Result compile(String parents, String inheritance, String methods) {
        return compiler().addSource("Child.java", """
                package example;
                import java.util.List;
                import java.util.Optional;
                import java.util.function.Consumer;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                %s
                @Data.Repository @Data.Provider("jdbc")
                public interface Child %s {
                    %s
                }
                """.formatted(parents, inheritance, methods)).build().compile();
    }

    private static TestCompiler.Builder compiler() {
        return TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(RuntimeType.class, Data.class, JdbcClient.class, Service.class, Tx.class,
                                      Generated.class, TypeName.class, DataGeneratorProvider.class,
                                      RepositoryCodegenProvider.class, JdbcRepositoryGeneratorProvider.class));
    }
}
