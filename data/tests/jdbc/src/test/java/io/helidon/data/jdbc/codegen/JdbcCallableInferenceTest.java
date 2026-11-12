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
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.sql.JDBCType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.builder.api.RuntimeType;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.AnnotationProperty;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.EnumValue;
import io.helidon.common.types.TypeName;
import io.helidon.data.Data;
import io.helidon.data.codegen.DataGeneratorProvider;
import io.helidon.data.codegen.common.RepositoryCodegenProvider;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.Service;
import io.helidon.transaction.Tx;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcCallableInferenceTest {
    @Test
    void infersPortableScalarRegistrationsAndOptionalWrappers() throws IOException {
        Map<String, String> types = new LinkedHashMap<>();
        types.put("boolean", "BOOLEAN");
        types.put("byte", "TINYINT");
        types.put("short", "SMALLINT");
        types.put("int", "INTEGER");
        types.put("long", "BIGINT");
        types.put("float", "REAL");
        types.put("double", "DOUBLE");
        types.put("Boolean", "BOOLEAN");
        types.put("Byte", "TINYINT");
        types.put("Short", "SMALLINT");
        types.put("Integer", "INTEGER");
        types.put("Long", "BIGINT");
        types.put("Float", "REAL");
        types.put("Double", "DOUBLE");
        types.put("BigDecimal", "DECIMAL");
        types.put("String", "VARCHAR");
        types.put("byte[]", "VARBINARY");
        types.put("LocalDate", "DATE");
        types.put("LocalTime", "TIME");
        types.put("LocalDateTime", "TIMESTAMP");
        types.put("java.sql.Date", "DATE");
        types.put("java.sql.Time", "TIME");
        types.put("java.sql.Timestamp", "TIMESTAMP");
        StringBuilder methods = new StringBuilder();
        int index = 0;
        for (Map.Entry<String, String> type : types.entrySet()) {
            methods.append("@Jdbc.Statement(\"{call routine(:out" + index + ")}\")\n")
                    .append("@Jdbc.OutParameter(\"out" + index + "\")\n")
                    .append(type.getKey()).append(" value").append(index).append("();\n");
            index++;
        }
        for (String type : List.of("Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double", "BigDecimal",
                                   "String", "byte[]", "LocalDate", "LocalTime", "LocalDateTime",
                                   "java.sql.Date", "java.sql.Time", "java.sql.Timestamp")) {
            methods.append("@Jdbc.Statement(\"{call routine(:out" + index + ")}\")\n")
                    .append("@Jdbc.OutParameter(\"out" + index + "\")\n")
                    .append("Optional<").append(type).append("> value").append(index).append("();\n");
            types.put("Optional<" + type + ">", types.get(type));
            index++;
        }
        TestCompiler.Result result = compile(methods.toString());
        assertThat(result.diagnostics().toString(), result.success(), is(true));
        String generated = generated(result);
        index = 0;
        for (String registration : types.values()) {
            assertThat(generated, containsString(".out(\"out" + index + "\", 1, JDBCType." + registration + ");"));
            index++;
        }
    }

    @Test
    void infersOutputRecordsAndMaterializedCursors() throws IOException {
        TestCompiler.Result result = compile("""
                record Row(int value) { }
                record Other(String label) { }
                record Outputs(List<Row> rows,
                                BigDecimal total,
                                Optional<String> status,
                                Optional<List<Integer>> checks,
                                Optional<Row> optionalRow,
                                Row oneRow) { }
                @Jdbc.Statement("{call routine(:MAPPER_AGGREGATE_OUTPUT_0_2, :rows, :total, :status, :checks, :optionalRow, :oneRow)}")
                @Jdbc.OutParameter(value = "rows")
                @Jdbc.OutParameter(value = "total", scale = 2)
                @Jdbc.OutParameter(value = "status")
                @Jdbc.OutParameter(value = "checks")
                @Jdbc.OutParameter(value = "optionalRow")
                @Jdbc.OutParameter(value = "oneRow")
                Outputs aggregate(String MAPPER_AGGREGATE_OUTPUT_0_2);
                @Jdbc.Statement("select 1")
                Other aggregate_OUTPUT_0();
                @Jdbc.Statement("{call routine(:rows)}") @Jdbc.OutParameter("rows")
                List<Row> rows();
                @Jdbc.Statement("{call routine(:rows)}") @Jdbc.OutParameter("rows")
                Optional<List<Integer>> optionalRows();
                @Jdbc.Statement("{call routine(:rows)}") @Jdbc.OutParameter("rows")
                Optional<Row> optionalRow();
                @Jdbc.Statement("{call routine(:rows)}") @Jdbc.OutParameter("rows")
                Row oneRow();
                """);
        assertThat(result.diagnostics().toString(), result.success(), is(true));
        String generated = generated(result);
        assertThat(generated, containsString(".out(\"total\", 3, JDBCType.DECIMAL, 2);"));
        assertThat(generated, containsString(".out(\"status\", 4, JDBCType.VARCHAR);"));
        assertThat(generated, containsString(".out(\"rows\", 1, JDBCType.REF_CURSOR);"));
        assertThat(generated, containsString(".optionalCursor(\"checks\""));
    }

    @Test
    void preservesExplicitOverridesAndResolvesTypedNullInout() throws IOException {
        TestCompiler.Result result = compile("""
                @Jdbc.Statement("{? = call routine()}") @Jdbc.OutParameter(type = JDBCType.NULL)
                String explicitNull();
                @Jdbc.Statement("{? = call routine()}") @Jdbc.OutParameter(type = JDBCType.NUMERIC)
                String converted();
                @Jdbc.Statement("{? = call routine()}") @Jdbc.OutParameter(type = JDBCType.NVARCHAR)
                String national();
                @Jdbc.Statement("{? = call routine()}") @Jdbc.OutParameter(type = JDBCType.REF_CURSOR)
                int scalarCursor();
                @Jdbc.Statement("{call routine(:value)}") @Jdbc.OutParameter(value = "value", scale = 2)
                BigDecimal inout(BigDecimal value);
                """);
        assertThat(result.diagnostics().toString(), result.success(), is(true));
        String generated = generated(result);
        for (String type : List.of("NULL", "NUMERIC", "NVARCHAR", "REF_CURSOR")) {
            assertThat(generated, containsString(".returns(JDBCType." + type + ");"));
        }
        assertThat(generated, containsString(".out(\"value\", 1, JDBCType.DECIMAL, 2);"));
        assertThat(generated, containsString("GeneratedJdbcData.bindNull(jdbcStatement, 1, JDBCType.DECIMAL);"));
    }

    @Test
    void requiresExplicitTypesWhenOutputShapeCannotDetermineRegistration() {
        for (String method : List.of(
                "void execute()",
                "JdbcClient.OutParameters execute()",
                "void execute(Consumer<JdbcClient.CallResult> action)",
                "String execute(JdbcClient.CallMapper<String> mapper)")) {
            TestCompiler.Result result = compile("""
                    @Jdbc.Statement("{call routine(:value)}") @Jdbc.OutParameter("value")
                    """ + method + ";");
            assertThat(result.diagnostics().toString(), result.success(), is(false));
            assertThat(result.diagnostics().toString(), containsString("explicit JDBC type"));
        }
        for (String attributes : List.of("typeName = \"APP.STATUS\"", "scale = 2")) {
            TestCompiler.Result result = compile("""
                    @Jdbc.Statement("{? = call routine()}")
                    @Jdbc.OutParameter(""" + attributes + ") String execute();");
            assertThat(result.diagnostics().toString(), result.success(), is(false));
        }
        TestCompiler.Result multiple = compile("""
                @Jdbc.Statement("{call routine(:value, :other)}")
                @Jdbc.OutParameter("value") @Jdbc.OutParameter(value = "other", type = JDBCType.INTEGER)
                int execute();
                """);
        assertThat(multiple.diagnostics().toString(), multiple.success(), is(false));
        assertThat(multiple.diagnostics().toString(), containsString("explicit JDBC type"));
    }

    @Test
    void rejectsAmbiguousAndUnsupportedInferredOutputs() {
        for (String type : List.of("List", "Optional", "Optional<Optional<Integer>>", "List<List<Integer>>",
                                   "Stream<Integer>", "java.sql.Blob", "java.util.UUID", "java.time.OffsetTime",
                                   "java.math.BigInteger", "Object")) {
            TestCompiler.Result result = compile("""
                    @Jdbc.Statement("{call routine(:value)}") @Jdbc.OutParameter("value")
                    """ + type + " execute();");
            assertThat(type + result.diagnostics(), result.success(), is(false));
        }
        TestCompiler.Result unresolved = compile("""
                @Jdbc.Statement("{? = call routine()}") @Jdbc.OutParameter
                <T> T execute();
                """);
        assertThat(unresolved.diagnostics().toString(), unresolved.success(), is(false));
        TestCompiler.Result scalarMapper = compile("""
                @Jdbc.Statement("{? = call routine()}") @Jdbc.OutParameter @Jdbc.RowMapper
                Integer execute();
                """);
        assertThat(scalarMapper.diagnostics().toString(), scalarMapper.success(), is(false));
        assertThat(scalarMapper.diagnostics().toString(), containsString("explicit REF_CURSOR"));
        TestCompiler.Result inout = compile("""
                @Jdbc.Statement("{call routine(:value)}") @Jdbc.OutParameter("value")
                int execute(String value);
                """);
        assertThat(inout.diagnostics().toString(), inout.success(), is(false));
        assertThat(inout.diagnostics().toString(), containsString("INOUT"));
    }

    @Test
    void resolvesEquivalentSourceAndSeparatelyCompiledGenericParents() throws IOException {
        for (boolean separate : List.of(false, true)) {
            TestCompiler.Builder child = compiler();
            TestCompiler.Builder parents = separate ? TestCompiler.builder().currentRelease()
                    .addClasspath(List.of(RuntimeType.class, JdbcClient.class)) : child;
            parents.addSource("example/Parent.java", """
                    package example;
                    import io.helidon.data.jdbc.Jdbc;
                    import java.sql.JDBCType;
                    public interface Parent<T> {
                        @Jdbc.Statement("{? = call routine(?)}") @Jdbc.OutParameter
                        T value(T value);
                        @Jdbc.Statement("{call routine(:total, :count)}")
                        @Jdbc.OutParameter("total") @Jdbc.OutParameter("count")
                        Outputs<T> aggregate();
                        @Jdbc.Statement("{? = call routine()}") @Jdbc.OutParameter(type = JDBCType.NULL)
                        String explicitNull();
                    }
                    """).addSource("example/Other.java", """
                    package example;
                    import io.helidon.data.jdbc.Jdbc;
                    import java.sql.JDBCType;
                    public interface Other {
                        @Jdbc.Statement("{? = call routine(?)}") @Jdbc.OutParameter(type = JDBCType.INTEGER)
                        Integer value(Integer value);
                        @Jdbc.Statement("{call routine(:total, :count)}")
                        @Jdbc.OutParameter(value = "count", type = JDBCType.INTEGER)
                        @Jdbc.OutParameter(value = "total", type = JDBCType.INTEGER)
                        Outputs<Integer> aggregate();
                    }
                    """).addSource("example/Outputs.java", """
                    package example;
                    import io.helidon.data.jdbc.Jdbc;
                    public record Outputs<T>(T total, int count) { }
                    """);
            if (separate) {
                TestCompiler.Result library = parents.build().compile();
                assertThat(library.diagnostics().toString(), library.success(), is(true));
                child.addClasspathEntry(library.classOutput());
            }
            TestCompiler.Result result = child.addSource("example/Repository.java", """
                    package example;
                    import io.helidon.data.Data;
                    @Data.Repository @Data.Provider("jdbc")
                    public interface Repository extends Parent<Integer>, Other { }
                    """).build().compile();
            assertThat("separate=" + separate + result.diagnostics(), result.success(), is(true));
            assertThat(generated(result), containsString(".out(\"total\", 1, JDBCType.INTEGER);"));
            assertThat(generated(result), containsString(".returns(JDBCType.NULL);"));
        }
    }

    @Test
    void rejectsGenuinelyDifferentInheritedRegistrations() {
        TestCompiler.Result result = compiler().addSource("example/Repository.java", """
                package example;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                import java.sql.JDBCType;
                import java.math.BigDecimal;
                interface First {
                    @Jdbc.Statement("{? = call routine()}") @Jdbc.OutParameter
                    BigDecimal value();
                }
                interface Second {
                    @Jdbc.Statement("{? = call routine()}") @Jdbc.OutParameter(type = JDBCType.NUMERIC)
                    BigDecimal value();
                }
                @Data.Repository @Data.Provider("jdbc")
                public interface Repository extends First, Second { }
                """).build().compile();
        assertThat(result.diagnostics().toString(), result.success(), is(false));
        assertThat(result.diagnostics().toString(), containsString("conflicting"));
    }

    @Test
    void matchesMethodOutputsToComponentsIndependentlyOfDeclarationOrder() throws IOException {
        TestCompiler.Result result = compile("""
                record Outputs(List<Row> rows, Optional<String> status, int checks) { }
                record Row(int id) { }
                @Jdbc.Statement("{call routine(:checks, :status, :rows)}")
                @Jdbc.OutParameter("status") @Jdbc.OutParameter("rows") @Jdbc.OutParameter("checks")
                Outputs aggregate(Integer checks);
                """);
        assertThat(result.diagnostics().toString(), result.success(), is(true));
        String source = generated(result);
        assertThat(source, containsString(".out(\"rows\", 3, JDBCType.REF_CURSOR)"));
        assertThat(source, containsString(".out(\"checks\", 1, JDBCType.INTEGER)"));
        assertThat(source.indexOf(".optional(\"status\"") < source.indexOf(".cursor(\"rows\""), is(true));
        assertThat(source, containsString("GeneratedJdbcData.bindNull(jdbcStatement, 1, JDBCType.INTEGER)"));
    }

    @Test
    void rejectsIncompleteAndIncompatibleAutomaticAggregates() {
        for (String outputs : List.of("@Jdbc.OutParameter(\"first\") @Jdbc.OutParameter(\"missing\")",
                                      "@Jdbc.OutParameter(\"First\") @Jdbc.OutParameter(\"second\")",
                                      "@Jdbc.OutParameter(\"first\") @Jdbc.OutParameter(\"first\")",
                                      "@Jdbc.OutParameter(\"first\") @Jdbc.OutParameter(\"second\") "
                                              + "@Jdbc.OutParameter(\"extra\")")) {
            TestCompiler.Result result = compile("""
                    record Outputs(int first, String second) { }
                    @Jdbc.Statement("{call routine(:first, :second)}")
                    """ + outputs + " Outputs execute();");
            assertThat(result.diagnostics().toString(), result.success(), is(false));
        }
        TestCompiler.Result incompatible = compile("""
                record Outputs(int first, List<Integer> second) { }
                @Jdbc.Statement("{call routine(:first, :second)}")
                @Jdbc.OutParameter("first") @Jdbc.OutParameter(value = "second", type = JDBCType.INTEGER)
                Outputs execute();
                """);
        assertThat(incompatible.diagnostics().toString(), incompatible.success(), is(false));
        assertThat(incompatible.diagnostics().toString(), containsString("scalar return type"));
        TestCompiler.Result mapper = compile("""
                record Outputs(int first, List<Integer> second) { }
                @Jdbc.Statement("{call routine(:first, :second)}") @Jdbc.RowMapper
                @Jdbc.OutParameter("first") @Jdbc.OutParameter("second")
                Outputs execute();
                """);
        assertThat(mapper.diagnostics().toString(), mapper.success(), is(false));
        assertThat(mapper.diagnostics().toString(), containsString("Use CallMapper"));
    }

    @Test
    void preservesCallbackPrecedenceForRecordReturns() {
        TestCompiler.Result result = compile("""
                record Outputs(int first, List<Integer> second) { }
                @Jdbc.Statement("{call routine(:first, :second)}")
                @Jdbc.OutParameter(value = "first", type = JDBCType.INTEGER)
                @Jdbc.OutParameter(value = "second", type = JDBCType.REF_CURSOR)
                Outputs execute(JdbcClient.CallMapper<Outputs> mapper);
                """);
        assertThat(result.diagnostics().toString(), result.success(), is(true));
    }

    @Test
    void preservesRegistrationThroughSourceCopyAndMetadataReconstruction() throws Exception {
        for (boolean explicit : List.of(false, true)) {
            Annotation output = Annotation.builder().type(Jdbc.OutParameter.class)
                    .putProperty("type", AnnotationProperty.create(EnumValue.create(JDBCType.class, JDBCType.NULL), explicit))
                    .build();
            Annotation statement = Annotation.builder().type(Jdbc.Statement.class).value("{? = call routine()}")
                    .build();
            ClassModel metadata = ClassModel.builder().type(TypeName.create("example.Metadata"))
                    .addMethod(method -> method.name("value").accessModifier(AccessModifier.PUBLIC).isStatic(true)
                            .returnType(TypeName.create(Annotation.class))
                            .addContent("return ").addContentCreate(output).addContentLine(";"))
                    .addMethod(method -> method.name("statement").accessModifier(AccessModifier.PUBLIC).isStatic(true)
                            .returnType(TypeName.create(Annotation.class))
                            .addContent("return ").addContentCreate(statement).addContentLine(";")).build();
            StringWriter metadataSource = new StringWriter();
            metadata.write(metadataSource);
            TestCompiler.Result library = compiler().addSource("example/Metadata.java", metadataSource.toString())
                    .build().compile();
            assertThat(library.diagnostics().toString(), library.success(), is(true));
            try (URLClassLoader loader = new URLClassLoader(new URL[] {library.classOutput().toUri().toURL()},
                                                            getClass().getClassLoader())) {
                // The generated public method exercises metadata reconstruction without inspecting private state.
                Annotation reconstructed = (Annotation) loader.loadClass("example.Metadata").getMethod("value").invoke(null);
                assertThat(reconstructed.property("type").orElseThrow().explicit(), is(explicit));
                Annotation copiedStatement = (Annotation) loader.loadClass("example.Metadata")
                        .getMethod("statement").invoke(null);
                assertThat(copiedStatement.stringValue(), is(statement.stringValue()));
                for (Annotation declaration : List.of(output, reconstructed)) {
                    StringWriter source = new StringWriter();
                    ClassModel.builder().type(TypeName.create("example.Repository")).classType(ElementKind.INTERFACE)
                            .addAnnotation(Annotation.create(Data.Repository.class))
                            .addAnnotation(Annotation.create(Data.Provider.class, "jdbc"))
                            .addMethod(method -> method.name("value").returnType(TypeName.create(String.class)).isAbstract(true)
                                    .addAnnotation(copiedStatement)
                                    .addAnnotation(declaration)).build().write(source);
                    TestCompiler.Result result = compiler().addSource("example/Repository.java", source.toString())
                            .build().compile();
                    assertThat(result.diagnostics().toString(), result.success(), is(true));
                    assertThat(generated(result), containsString(".returns(JDBCType."
                            + (explicit ? "NULL" : "VARCHAR") + ");"));
                }
            }
        }
    }

    @Test
    void preservesDefaultsInsideCopiedRepeatableOutputContainers() throws IOException {
        AnnotationProperty omitted = AnnotationProperty.create(EnumValue.create(JDBCType.class, JDBCType.NULL), false);
        Annotation first = Annotation.builder().type(Jdbc.OutParameter.class).value("text")
                .putProperty("type", omitted).build();
        Annotation second = Annotation.builder().type(Jdbc.OutParameter.class).value("count")
                .putProperty("type", omitted).build();
        Annotation outputs = Annotation.builder().type(Jdbc.OutParameterDeclarations.class)
                .property("value", List.of(first, second)).build();
        StringWriter source = new StringWriter();
        ClassModel.builder().type(TypeName.create("example.Repository")).classType(ElementKind.INTERFACE)
                .addAnnotation(Annotation.create(Data.Repository.class))
                .addAnnotation(Annotation.create(Data.Provider.class, "jdbc"))
                .addMethod(method -> method.name("value").returnType(TypeName.create("example.Outputs")).isAbstract(true)
                        .addAnnotation(Annotation.create(Jdbc.Statement.class, "{call routine(:count, :text)}"))
                        .addAnnotation(outputs)).build().write(source);
        TestCompiler.Result result = compiler().addSource("example/Repository.java", source.toString())
                .addSource("example/Outputs.java", "package example; public record Outputs(String text, int count) { }")
                .build().compile();
        assertThat(result.diagnostics().toString(), result.success(), is(true));
        assertThat(generated(result), containsString(".out(\"text\", 2, JDBCType.VARCHAR)"));
        assertThat(generated(result), containsString(".out(\"count\", 1, JDBCType.INTEGER)"));
        TestCompiler.Result child = compiler().addClasspathEntry(result.classOutput())
                .addSource("example/Child.java", """
                        package example;
                        import io.helidon.data.Data;
                        @Data.Repository @Data.Provider("jdbc")
                        public interface Child extends Repository { }
                        """).build().compile();
        assertThat(child.diagnostics().toString(), child.success(), is(true));
        String inherited = Files.readString(child.sourceOutput().resolve("example/Child__Jdbc.java"));
        assertThat(inherited, containsString(".out(\"text\", 2, JDBCType.VARCHAR)"));
        assertThat(inherited, containsString(".out(\"count\", 1, JDBCType.INTEGER)"));
    }

    @Test
    void diagnosesSourceComponentDeclarationsAfterMethodTargetPropagation() {
        for (String type : List.of("Legacy", "List<Legacy>", "Optional<Legacy>")) {
            TestCompiler.Result result = compile("""
                    record Legacy(@Jdbc.OutParameter int value) { }
                    @Jdbc.Statement("{call routine(:value)}")
                    """ + type + " value();");
            assertThat(result.diagnostics().toString(), result.success(), is(false));
            assertThat(result.diagnostics().toString(), containsString("Move output declarations"));
        }
    }

    @Test
    void diagnosesLegacyCompiledComponentDeclarations() {
        for (String declaration : List.of("@Jdbc.OutParameter", "@Jdbc.OutParameterDeclarations({@Jdbc.OutParameter})")) {
            TestCompiler.Result library = TestCompiler.builder().currentRelease().printDiagnostics(false)
                    .addSource("io/helidon/data/jdbc/Jdbc.java", """
                            package io.helidon.data.jdbc;
                            import java.lang.annotation.*;
                            public class Jdbc {
                                @Target(ElementType.METHOD) @Retention(RetentionPolicy.CLASS)
                                public @interface Statement { String value(); }
                                @Target(ElementType.RECORD_COMPONENT) @Retention(RetentionPolicy.CLASS)
                                public @interface OutParameter { }
                                @Target(ElementType.RECORD_COMPONENT) @Retention(RetentionPolicy.CLASS)
                                public @interface OutParameterDeclarations { OutParameter[] value(); }
                            }
                            """).addSource("example/Legacy.java", """
                            package example;
                            import io.helidon.data.jdbc.Jdbc;
                            public record Legacy(""" + declaration + " int value) { }").build().compile();
            assertThat(library.diagnostics().toString(), library.success(), is(true));
            TestCompiler.Result result = compiler().addClasspathEntry(library.classOutput())
                    .addSource("example/Repository.java", """
                            package example;
                            import io.helidon.data.Data;
                            import io.helidon.data.jdbc.Jdbc;
                            @Data.Repository @Data.Provider("jdbc")
                            public interface Repository {
                                @Jdbc.Statement("{call routine(:value)}") Legacy value();
                            }
                            """).build().compile();
            assertThat(result.diagnostics().toString(), result.success(), is(false));
            assertThat(result.diagnostics().toString(), containsString("Move output declarations"));
        }
    }

    private static String generated(TestCompiler.Result result) throws IOException {
        return Files.readString(result.sourceOutput().resolve("example/Repository__Jdbc.java"));
    }

    private static TestCompiler.Result compile(String declarations) {
        return compiler().addSource("example/Repository.java", """
                package example;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                import io.helidon.data.jdbc.JdbcClient;
                import java.sql.JDBCType;
                import java.math.BigDecimal;
                import java.time.LocalDate;
                import java.time.LocalTime;
                import java.time.LocalDateTime;
                import java.util.List;
                import java.util.Optional;
                import java.util.function.Consumer;
                import java.util.stream.Stream;
                @Data.Repository @Data.Provider("jdbc")
                public interface Repository {
                """ + declarations + "\n}").build().compile();
    }

    private static TestCompiler.Builder compiler() {
        return TestCompiler.builder().currentRelease().printDiagnostics(false).addProcessor(AptProcessor::new)
                .addClasspath(List.of(RuntimeType.class, Data.class, JdbcClient.class, Service.class, Tx.class,
                                      Generated.class, TypeName.class, DataGeneratorProvider.class,
                                      RepositoryCodegenProvider.class, JdbcRepositoryGeneratorProvider.class));
    }
}
