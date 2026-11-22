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

package io.helidon.json.tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.helidon.codegen.api.stability.ApiStabilityProcessor;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Api;
import io.helidon.common.Generated;
import io.helidon.common.buffers.Bytes;
import io.helidon.json.JsonGenerator;
import io.helidon.json.binding.Json;
import io.helidon.service.registry.Service;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

@SuppressWarnings({Api.SUPPRESS_INTERNAL, Api.SUPPRESS_PREVIEW})
class GeneratedSourceMetadataTest {
    private static final String PACKAGE_NAME = "io.helidon.json.tests.generated";
    private static final String PACKAGE_PATH = PACKAGE_NAME.replace('.', '/') + "/";
    private static final String GENERATOR = "io.helidon.json.codegen.JsonCodegen";
    private static final List<Class<?>> CLASSPATH = List.of(
            Generated.class,
            Bytes.class,
            JsonGenerator.class,
            Json.class,
            Service.class);

    @Test
    void testGeneratedConverterMetadata() throws IOException {
        var result = compile("StringWrapper.java", """
                package io.helidon.json.tests.generated;
                
                import io.helidon.json.binding.Json;
                
                @Json.Entity
                public class StringWrapper {
                    private String value;
                
                    public String getValue() {
                        return value;
                    }
                
                    public void setValue(String value) {
                        this.value = value;
                    }
                }
                """);

        assertCompilationSuccess(result);
        assertGeneratedSourceMetadata(generatedSource(result, "StringWrapper__GeneratedConverter.java"),
                                      GENERATOR,
                                      PACKAGE_NAME + ".StringWrapper");
    }

    @Test
    void testGeneratedBindingFactoryMetadata() throws IOException {
        var result = compile("Container.java", """
                package io.helidon.json.tests.generated;
                
                import io.helidon.json.binding.Json;
                
                @Json.Entity
                public class Container<T> {
                    private T value;
                
                    public T getValue() {
                        return value;
                    }
                
                    public void setValue(T value) {
                        this.value = value;
                    }
                }
                """);

        assertCompilationSuccess(result);
        assertGeneratedSourceMetadata(generatedSource(result, "Container_BindingFactory.java"),
                                      GENERATOR,
                                      PACKAGE_NAME + ".Container<T>");
    }

    @Test
    void namesPropertyCasesWithJsonNames() throws IOException {
        var result = compile("NamedProperties.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                public record NamedProperties(String project, @Json.Property("wireName") int value) {
                }
                """);

        assertCompilationSuccess(result);
        String content = Files.readString(generatedSource(result, "NamedProperties__GeneratedConverter.java"));
        assertOrdinalCases(content,
                          List.of("PROJECT", "WIRE_NAME"),
                          List.of("case PROJECT -> {", "case WIRE_NAME -> {"));
        assertThat(content.replace("\r\n", "\n"),
                   containsString("                case PROJECT -> {\n                    project_ ="));
        assertThat(content.replace("\r\n", "\n"),
                   containsString("                case WIRE_NAME -> {\n                    wireName_ ="));
    }

    @Test
    void normalizesPropertyNamesInCaseConstants() throws IOException {
        var result = compile("UnusualNames.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                public class UnusualNames {
                    private String newline;
                    private String unicode;
                    private String markers;
                    private String quoted;

                    @Json.Property("line\\r\\nbreak")
                    public void setNewline(String value) {
                        newline = value;
                    }

                    @Json.Property("unicode\\\\u000a")
                    public void setUnicode(String value) {
                        unicode = value;
                    }

                    @Json.Property("comment*/tail//suffix")
                    public void setMarkers(String value) {
                        markers = value;
                    }

                    @Json.Property("quote\\\"\\\\slash")
                    public void setQuoted(String value) {
                        quoted = value;
                    }
                }
                """);

        assertCompilationSuccess(result);
        String content = Files.readString(generatedSource(result, "UnusualNames__GeneratedConverter.java"));
        assertOrdinalCases(content,
                          List.of("LINE__BREAK", "UNICODE_U000A", "COMMENT__TAIL__SUFFIX", "QUOTE__SLASH"),
                          List.of("case LINE__BREAK -> { // \"line\\r\\nbreak\"",
                                  "case UNICODE_U000A -> { // \"unicode\\\\u000a\"",
                                  "case COMMENT__TAIL__SUFFIX -> { // \"comment*/tail//suffix\"",
                                  "case QUOTE__SLASH -> { // \"quote\\\"\\\\slash\""));
    }

    @Test
    void disambiguatesNormalizedPropertyConstants() throws IOException {
        var result = compile("NormalizedProperties.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                public record NormalizedProperties(@Json.Property("my-key") String dashed,
                                                   @Json.Property("my_key") String underscored,
                                                   @Json.Property("") String empty,
                                                   @Json.Property("_") String underscore,
                                                   @Json.Property("name") String plain) {
                }
                """);

        assertCompilationSuccess(result);
        String content = Files.readString(generatedSource(result, "NormalizedProperties__GeneratedConverter.java"));
        assertOrdinalCases(content,
                          List.of("MY_KEY", "MY_KEY_1", "NAME", "NAME_1", "NAME_2"),
                          List.of("case MY_KEY -> { // \"my-key\"",
                                  "case MY_KEY_1 -> { // \"my_key\"",
                                  "case NAME -> { // \"\"",
                                  "case NAME_1 -> { // \"_\"",
                                  "case NAME_2 -> { // \"name\""));
        assertThat(content.replace("\r\n", "\n"),
                   containsString("                case MY_KEY -> { // \"my-key\"\n                    my_key_ ="));
    }

    @Test
    void propertyConstantsShareTheGeneratedFieldNamespace() throws IOException {
        var result = compile("ReservedProperties.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.common.GenericType;
                import io.helidon.json.JsonParser;
                import io.helidon.json.binding.Json;
                import io.helidon.json.binding.JsonDeserializer;

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                public record ReservedProperties(String propertyNames,
                                                 String firstDeserializer,
                                                 @Json.Deserializer(CustomDeserializer.class) String first,
                                                 String firstKey) {
                    public static class CustomDeserializer implements JsonDeserializer<String> {
                        @Override
                        public String deserialize(JsonParser parser) {
                            return parser.readString();
                        }

                        @Override
                        public GenericType<String> type() {
                            return GenericType.create(String.class);
                        }
                    }
                }
                """);

        assertCompilationSuccess(result);
        String content = Files.readString(generatedSource(result, "ReservedProperties__GeneratedConverter.java"));
        assertOrdinalCases(content,
                          List.of("PROPERTY_NAMES_1", "FIRST_DESERIALIZER", "FIRST", "FIRST_KEY_1"),
                          List.of("case PROPERTY_NAMES_1 -> { // \"propertyNames\"",
                                  "case FIRST_DESERIALIZER -> {",
                                  "case FIRST -> {",
                                  "case FIRST_KEY_1 -> { // \"firstKey\""));
        assertThat(content, containsString("JsonStringLexer PROPERTY_NAMES ="));
        assertThat(content, containsString("JsonKey FIRST_KEY = JsonKey.create(\"first\")"));
        assertThat(content, containsString("CustomDeserializer FIRST_DESERIALIZER_1 ="));
        assertThat(content, containsString("FIRST_DESERIALIZER_1.deserialize(parser)"));
    }

    @Test
    void projectedNamesCanShareAPackageWithATypeNamedLong() throws IOException {
        var result = compile("Projected.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                public record Projected(int a, int ab, int prefix00aa, int prefix00ab, int prefix01aa, int prefix01ab,
                                        int prefix02aa, int prefix02ab, int prefix03aa, int prefix03ab, int prefix04aa,
                                        int prefix04ab) {
                }

                class Long {
                }
                """);

        assertCompilationSuccess(result);
        String content = Files.readString(generatedSource(result, "Projected__GeneratedConverter.java"));
        assertThat(content, containsString("import static java.lang.Long.compress;"));
        assertThat(content, containsString("compress(second,"));
    }

    @Test
    void propertyConstantsDoNotShadowCreatorTypes() throws IOException {
        var result = compile("URL.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                public class URL {
                    private final String url;

                    private URL(String url) {
                        this.url = url;
                    }

                    @Json.Creator
                    public static URL create(String url) {
                        return new URL(url);
                    }

                    public String getUrl() {
                        return url;
                    }

                    @Json.Entity
                    public static class Data {
                        private final String url;

                        private Data(String url) {
                            this.url = url;
                        }

                        @Json.Creator
                        public static Data create(String url) {
                            return new Data(url);
                        }

                        public String getUrl() {
                            return url;
                        }
                    }
                }
                """);

        assertCompilationSuccess(result);
        for (String file : List.of("URL__GeneratedConverter.java", "URL_Data__GeneratedConverter.java")) {
            String content = Files.readString(generatedSource(result, file));
            assertOrdinalCases(content, List.of("URL_1"), List.of("case URL_1 -> { // \"url\""));
        }
    }

    @Test
    void sharesNamedAliasCasesBetweenDiscriminatorPositions() throws IOException {
        var result = compile("NamedAliases.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                public class NamedAliases {
                    @Json.Entity
                    @Json.Subtype(value = Sample.class, alias = "sample")
                    public interface Parent {
                    }

                    @Json.Entity
                    public record Sample(String value) implements Parent {
                    }
                }
                """);

        assertCompilationSuccess(result);
        String content = Files.readString(generatedSource(result, "NamedAliases_Parent__GeneratedPolyConverter.java"));
        assertOrdinalCases(content,
                          List.of("SAMPLE"),
                          List.of("case SAMPLE -> {", "case SAMPLE -> {"));
    }

    @Test
    void disambiguatesAliasConstantsAndCommentsOriginalNames() throws IOException {
        var result = compile("NormalizedAliases.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                public class NormalizedAliases {
                    @Json.Entity
                    @Json.Subtype(value = Dashed.class, alias = "my-key")
                    @Json.Subtype(value = Underscored.class, alias = "my_key")
                    @Json.Subtype(value = Reserved.class, alias = "polymorphicAliases")
                    @Json.Subtype(value = Markers.class, alias = "comment*/tail//suffix")
                    public interface Parent {
                    }

                    @Json.Entity
                    public record Dashed(String value) implements Parent {
                    }

                    @Json.Entity
                    public record Underscored(String value) implements Parent {
                    }

                    @Json.Entity
                    public record Reserved(String value) implements Parent {
                    }

                    @Json.Entity
                    public record Markers(String value) implements Parent {
                    }
                }
                """);

        assertCompilationSuccess(result);
        String content = Files.readString(generatedSource(result, "NormalizedAliases_Parent__GeneratedPolyConverter.java"));
        assertOrdinalCases(content,
                          List.of("MY_KEY", "MY_KEY_1", "POLYMORPHIC_ALIASES_1", "COMMENT__TAIL__SUFFIX"),
                          List.of("case MY_KEY -> { // \"my-key\"",
                                  "case MY_KEY_1 -> { // \"my_key\"",
                                  "case POLYMORPHIC_ALIASES_1 -> { // \"polymorphicAliases\"",
                                  "case COMMENT__TAIL__SUFFIX -> { // \"comment*/tail//suffix\"",
                                  "case MY_KEY -> { // \"my-key\"",
                                  "case MY_KEY_1 -> { // \"my_key\"",
                                  "case POLYMORPHIC_ALIASES_1 -> { // \"polymorphicAliases\"",
                                  "case COMMENT__TAIL__SUFFIX -> { // \"comment*/tail//suffix\""));
        assertThat(content, containsString("JsonStringLexer POLYMORPHIC_ALIASES ="));
    }

    @Test
    void appendsNamedMetadataCasesAfterDataProperties() throws IOException {
        var result = compile("MetadataProperties.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                @Json.Polymorphic(key = "my-key")
                @Json.Subtype(value = MetadataProperties.class, alias = "sample")
                interface MetadataParent {
                }

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                public record MetadataProperties(@Json.Property("my_key") String value, int count) implements MetadataParent {
                }
                """);

        assertCompilationSuccess(result);
        String content = Files.readString(generatedSource(result, "MetadataProperties__GeneratedConverter.java"));
        assertOrdinalCases(content,
                          List.of("MY_KEY", "COUNT", "MY_KEY_1"),
                          List.of("case MY_KEY -> {", "case COUNT -> {", "case MY_KEY_1 -> { // \"my-key\""));
        assertThat(content.replace("\r\n", "\n"),
                   containsString("case MY_KEY_1 -> { // \"my-key\"\n                    parser.skip();\n"));
        assertThat(content, not(containsString("_ALIASES")));
    }

    @Test
    void ordersSerializationChecksBySubtypeSpecificity() throws IOException {
        var result = compile("OrderedTypes.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                public class OrderedTypes {
                    @Json.Entity
                    @Json.Subtype(value = Cat.class, alias = "cat")
                    @Json.Subtype(value = BrownCat.class, alias = "brown")
                    public static class Animal {
                        public String name;
                    }

                    @Json.Entity
                    public static class Cat extends Animal {
                        public int lives;
                    }

                    @Json.Entity
                    public static class BrownCat extends Cat {
                        public String shade;
                    }
                }
                """);

        assertCompilationSuccess(result);
        String content = Files.readString(generatedSource(result, "OrderedTypes_Animal__GeneratedPolyConverter.java"));
        String leafCheck = "instance instanceof OrderedTypes.BrownCat";
        String parentCheck = "instance instanceof OrderedTypes.Cat";
        assertThat(content, containsString(leafCheck));
        assertThat(content, containsString(parentCheck));
        assertThat(content.indexOf(leafCheck), lessThan(content.indexOf(parentCheck)));
    }

    @Test
    void rejectsConflictingDiscriminatorKeysInAStagedHierarchy() {
        for (String alias : List.of("brown", "cat")) {
            var result = compile("ConflictingTypes.java", """
                    package io.helidon.json.tests.generated;

                    import io.helidon.json.binding.Json;

                    @SuppressWarnings("helidon:api:preview")
                    public class ConflictingTypes {
                        @Json.Entity
                        @Json.Polymorphic(key = "kind")
                        @Json.Subtype(value = Cat.class, alias = "cat")
                        public static class Animal {
                            public String name;
                        }

                        @Json.Entity
                        @Json.Polymorphic(key = "kind")
                        @Json.Subtype(value = BrownCat.class, alias = "%s")
                        public static class Cat extends Animal {
                            public int lives;
                        }

                        @Json.Entity
                        public static class BrownCat extends Cat {
                            public String shade;
                        }
                    }
                    """.formatted(alias));

            assertThat(result.success(), is(false));
            assertThat(String.join(System.lineSeparator(), result.diagnostics()),
                       containsString("Conflicting polymorphic discriminator key"));
        }
    }

    @Test
    void rejectsConflictingDiscriminatorKeysThroughAGenericParent() {
        var result = compile("GenericConflictingTypes.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                public class GenericConflictingTypes {
                    @Json.Entity
                    @Json.Subtype(value = Cat.class, alias = "cat")
                    public static class Animal {
                    }

                    @Json.Entity
                    @Json.Subtype(value = BrownCat.class, alias = "brown")
                    public static class Cat<T> extends Animal {
                        public T value;
                    }

                    @Json.Entity
                    public static class BrownCat extends Cat<String> {
                    }
                }
                """);

        assertThat(result.success(), is(false));
        assertThat(String.join(System.lineSeparator(), result.diagnostics()),
                   containsString("Conflicting polymorphic discriminator key"));
    }

    @Test
    void rejectsConflictingDiscriminatorKeysWithAnUnannotatedLeaf() {
        var result = compile("UnannotatedLeafTypes.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                public class UnannotatedLeafTypes {
                    @Json.Entity
                    @Json.Subtype(value = Cat.class, alias = "cat")
                    public static class Animal {
                    }

                    @Json.Entity
                    @Json.Subtype(value = BrownCat.class, alias = "brown")
                    public static class Cat extends Animal {
                    }

                    public static class BrownCat extends Cat {
                    }
                }
                """);

        assertThat(result.success(), is(false));
        assertThat(String.join(System.lineSeparator(), result.diagnostics()),
                   containsString("Conflicting polymorphic discriminator key"));
    }

    @Test
    void rejectsConflictingAliasesFromIndependentParents() {
        var result = compile("ConflictingParentTypes.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                public class ConflictingParentTypes {
                    @Json.Entity
                    @Json.Subtype(value = Leaf.class, alias = "left")
                    public interface Left {
                    }

                    @Json.Entity
                    @Json.Subtype(value = Leaf.class, alias = "right")
                    public interface Right {
                    }

                    @Json.Entity
                    public record Leaf(String value) implements Left, Right {
                    }
                }
                """);

        assertThat(result.success(), is(false));
        assertThat(String.join(System.lineSeparator(), result.diagnostics()),
                   containsString("Conflicting polymorphic discriminator key"));
    }

    @Test
    void allowsMatchingDiscriminatorMetadataThroughADiamond() throws IOException {
        var result = compile("DiamondTypes.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                public class DiamondTypes {
                    @Json.Entity
                    @Json.Polymorphic(key = "kind")
                    @Json.Subtype(value = Leaf.class, alias = "leaf")
                    public interface Root {
                    }

                    public interface Left extends Root {
                    }

                    public interface Right extends Root {
                    }

                    @Json.Entity
                    public record Leaf(String value) implements Left, Right {
                    }
                }
                """);

        assertCompilationSuccess(result);
        String content = Files.readString(generatedSource(result, "DiamondTypes_Leaf__GeneratedConverter.java"));
        assertThat(content.lines().filter(line -> line.contains("generator.write(\"kind\", \"leaf\")")).count(), is(1L));
    }

    private static void assertOrdinalCases(String content, List<String> names, List<String> cases) {
        List<String> dispatchCases = new ArrayList<>();
        int switchIndent = -1;
        for (String line : content.lines().toList()) {
            String text = line.strip();
            int indent = line.length() - line.stripLeading().length();
            if (text.endsWith("switch (property) {") || text.endsWith("switch (alias) {")) {
                switchIndent = indent;
            } else if (indent == switchIndent && text.startsWith("}")) {
                switchIndent = -1;
            } else if (switchIndent >= 0 && text.startsWith("case ")) {
                dispatchCases.add(text);
            }
        }
        assertThat(dispatchCases, is(cases));
        for (int index = 0; index < names.size(); index++) {
            assertThat(content, containsString("private static final int " + names.get(index) + " = " + index + ";"));
        }
    }

    private static TestCompiler.Result compile(String sourceName, String source) {
        return TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addProcessor(ApiStabilityProcessor::new)
                .addOption("-Ahelidon.api=fail")
                .addSource(sourceName, source)
                .build()
                .compile();
    }

    private static void assertCompilationSuccess(TestCompiler.Result result) {
        assertThat(String.join(System.lineSeparator(), result.diagnostics()), result.success(), is(true));
    }

    private static Path generatedSource(TestCompiler.Result result, String fileName) {
        return result.sourceOutput().resolve(PACKAGE_PATH + fileName);
    }

    private static void assertGeneratedSourceMetadata(Path generatedSource,
                                                      String generator,
                                                      String trigger) throws IOException {
        assertThat("Generated source should exist: " + generatedSource, Files.exists(generatedSource), is(true));

        String content = Files.readString(generatedSource);

        assertThat(content, startsWith("/*"));
        assertThat(content, containsString("Copyright (c) "));
        assertThat(content, containsString(" Oracle and/or its affiliates."));
        assertThat(content, containsString("Licensed under the Apache License, Version 2.0"));
        assertThat(content, not(containsString("// This is a generated file (powered by Helidon).")));
        assertThat(content, containsString("@Generated("));
        assertThat(content, containsString("@SuppressWarnings(\"" + Api.SUPPRESS_ALL + "\")"));
        assertThat(content, containsString("value = \"" + generator + "\""));
        assertThat(content, containsString("trigger = \"" + trigger + "\""));
    }
}
