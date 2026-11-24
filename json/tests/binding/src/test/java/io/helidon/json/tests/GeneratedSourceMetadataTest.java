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
        assertOrdinalCases(content, 2,
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
        assertOrdinalCases(content, 1,
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
        assertOrdinalCases(content, 2,
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
        assertOrdinalCases(content, 2,
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
    void usesACompleteDictionaryForMediumAsciiPropertySets() throws IOException {
        var result = compile("Order.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                public record Order(int ts, int kind, int traceId, int durationNanos, int host, int severity,
                                    int operationName, int eventTimestamp, int bytes, int parentSpanId, int resourceId,
                                    int sequence, int elapsedMillis, int retries, int errorCode, int httpStatus, int path,
                                    int method, int route, int requestBytes, int responseBytes, int startedAt,
                                    int sampled, int serviceName, int serviceVersion, int zone, int endpoint,
                                    int protocol, int attempt, int spanId, int clientRegion, int correlationId) {
                }

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                record ByteCodes(int addressCode, int billingRate, int customerRef, int deliveryZone, int eventTime,
                                 int feeAmount, int grossTotal, int hotelCode, int itemCount, int jobNumber,
                                 int ledgerRef, int marketCode, int netAmount, int orderDate, int partNumber, int quoteTotal) {
                }

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                record Shared(int shipmentId, int shipmentDate, int shipmentTotal, int shipmentTax, int shipmentRate,
                              int shipmentCode, int lineCount, int amount, int currency, int region, int createdAt,
                              int updatedAt, int status, int postalCode, int accountNumber, int unitPrice) {
                }
                """);

        assertCompilationSuccess(result);
        String ordinary = Files.readString(generatedSource(result, "Order__GeneratedConverter.java"));
        assertThat(ordinary, containsString("JsonStringLexer PROPERTY_NAMES = JsonStringLexer.create("));
        String byByte = Files.readString(generatedSource(result, "ByteCodes__GeneratedConverter.java"));
        assertThat(byByte, containsString("JsonStringLexer PROPERTY_NAMES = createPropertyNameLexer("));
        assertThat(byByte, containsString("JsonStringLexer.withAscii(JsonStringLexer.create(names),"));
        String shared = Files.readString(generatedSource(result, "Shared__GeneratedConverter.java"));
        assertThat(shared, containsString("JsonStringLexer PROPERTY_NAMES = JsonStringLexer.withAscii("));
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
    void selectsOverlappingShortFieldsByTheirEncodedLength() throws IOException {
        var result = compile("ShortMetrics.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                public record ShortMetrics(int max, int idMax, int count, int in, int minId, int countA, int minMs, int inMs,
                                           int valMs, int cpuMax, int min, int unitA, @Json.Property("") int empty,
                                           int offPeak, int off) {
                }

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                record TooManyBits(@Json.Property("") int empty, int px0, int px1, int px2, int px3, int px4, int px5, int px6,
                                   int paaaaaa, int pcaaaaa, int peaaaaa, int piaaaaa, int pqaaaaa,
                                   @Json.Property("pAaaaaa") int upper, int pacaaaa, int paeaaaa) {
                }

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                record Direct(int max, int idMax, int count, int in, int minId, int countA, int minMs, int inMs,
                              int valMs, int cpuMax, int min, int unitA) {
                }

                class Long {
                }
                """);

        assertCompilationSuccess(result);
        String content = Files.readString(generatedSource(result, "ShortMetrics__GeneratedConverter.java"));
        assertThat(content, containsString("import static java.lang.Long.numberOfTrailingZeros;"));
        assertThat(content, containsString("long quotes = first ^ 0x2222222222222222L;"));
        assertThat(content, containsString("numberOfTrailingZeros((quotes - 0x0101010101010101L) & ~quotes"
                                                 + " & 0x8080808080808080L) / 8;"));
        assertThat(content, containsString("case 0 -> 12;"));
        assertThat(content, containsString("case 7 -> 13;"));
        assertThat(content, containsString("case 5 -> PROPERTY_NAMES_TRANSITIONS[4 + (int) compress(first,"));
        assertThat(content, containsString("case 6 -> PROPERTY_NAMES_TRANSITIONS[12 + (int) compress(first,"));
        String fallback = Files.readString(generatedSource(result, "TooManyBits__GeneratedConverter.java"));
        assertThat(fallback, containsString("::lexPropertyNameAscii"));
        assertThat(fallback, not(containsString("numberOfTrailingZeros(")));
        String direct = Files.readString(generatedSource(result, "Direct__GeneratedConverter.java"));
        assertThat(direct, containsString("int route = PROPERTY_NAMES_TRANSITIONS["));
        assertThat(direct, not(containsString("numberOfTrailingZeros(")));
    }

    @Test
    void propertyLoopVariableDoesNotShadowTheCreatorType() throws IOException {
        var result = compile("property.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                public class property {
                    private final int id;

                    private property(int id) {
                        this.id = id;
                    }

                    @Json.Creator
                    public static property create(int id) {
                        return new property(id);
                    }

                    public int getId() {
                        return id;
                    }
                }
                """);

        assertCompilationSuccess(result);
        String content = Files.readString(generatedSource(result, "property__GeneratedConverter.java"));
        assertThat(content, containsString("for (int property = parser.readProperty(PROPERTY_NAMES);"));
        assertThat(content, containsString("property.create("));
    }

    @Test
    void emitsBoundsForPartiallyFilledDecodedRanges() throws IOException {
        var result = compile("Counters.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                public record Counters(int id, int stat00k, int stat01k, int stat02k, int stat03k, int stat04k, int stat05k,
                                       int stat06k, int stat07k, int stat08k, int stat09k, int stat10k, int stat11k,
                                       int stat12k, int stat13k, int stat14k, int stat15k, int stat16k) {
                }
                """);

        assertCompilationSuccess(result);
        String content = Files.readString(generatedSource(result, "Counters__GeneratedConverter.java"));
        assertThat(content, containsString("digit0 + 10 * digit1"));
        assertThat(content, containsString("index < 17 ? 1 + index : -1"));
    }

    @Test
    void selectsPastALongSharedWirePrefix() throws IOException {
        var result = compile("LongFields.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                public record LongFields(@Json.Property("shipping.destination.address.postalCode") int code,
                                         @Json.Property("shipping.destination.address.streetAddress") int street) {
                }
                """);

        assertCompilationSuccess(result);
        String content = Files.readString(generatedSource(result, "LongFields__GeneratedConverter.java"));
        assertThat(content, containsString("::lexPropertyNameUtf8, 29,"));
    }

    @Test
    void wideIntegerCreatorsReadKnownValuesAtOneSite() throws IOException {
        List<String> components = new ArrayList<>();
        for (int i = 0; i < 63; i++) {
            components.add("int p" + i);
        }
        var result = compile("WideCounters.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                public record WideCounters(%1$s, int last) {
                }

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                record SmallerCounters(%1$s) {
                }

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                record MixedCounters(%1$s, long last) {
                }
                """.formatted(String.join(", ", components)));

        assertCompilationSuccess(result);
        String wide = Files.readString(generatedSource(result, "WideCounters__GeneratedConverter.java"));
        assertThat(wide.lines().filter(line -> line.contains("deserializerInt.deserialize(parser)")).count(), is(1L));
        for (String type : List.of("SmallerCounters", "MixedCounters")) {
            String general = Files.readString(generatedSource(result, type + "__GeneratedConverter.java"));
            long reads = general.lines().filter(line -> line.contains("deserializerInt.deserialize(parser)")).count();
            assertThat(type, reads, is(63L));
        }
    }

    @Test
    void orderedBeanReusesItsInstanceAndKeepsAGeneralReader() throws IOException {
        var result = compile("OrderedBean.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                public class OrderedBean {
                    public int id;
                    public String label;
                    public int count;
                }
                """);

        assertCompilationSuccess(result);
        String content = Files.readString(generatedSource(result, "OrderedBean__GeneratedConverter.java"));
        assertThat(content, containsString("parser.tryReadProperty(ID_KEY)"));
        assertThat(content, containsString("return readDeclaredFields(parser, instance)"));
        assertThat(content, containsString("private OrderedBean readDeclaredFields(JsonParser parser, OrderedBean instance)"));
        assertThat(content, containsString("parser.tryNextProperty(LABEL_KEY)"));
        assertThat(content, containsString("new OrderedBean()"));
        assertThat(content.indexOf("new OrderedBean()"), is(content.lastIndexOf("new OrderedBean()")));
    }

    @Test
    void orderedReaderReceivesTheBuilderCreatedAtObjectStart() throws IOException {
        var result = compile("BuilderBacked.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                @Json.Entity
                @Json.BuilderInfo(BuilderBacked.Builder.class)
                public class BuilderBacked {
                    private final int id;
                    private final String label;

                    private BuilderBacked(Builder builder) {
                        id = builder.id;
                        label = builder.label;
                    }

                    public int id() {
                        return id;
                    }

                    public String label() {
                        return label;
                    }

                    public static class Builder {
                        private int id;
                        private String label;

                        public Builder id(int value) {
                            id = value;
                            return this;
                        }

                        public Builder label(String value) {
                            label = value;
                            return this;
                        }

                        public BuilderBacked build() {
                            return new BuilderBacked(this);
                        }
                    }
                }
                """);

        assertCompilationSuccess(result);
        String content = Files.readString(generatedSource(result, "BuilderBacked__GeneratedConverter.java"));
        assertThat(content, containsString("return readDeclaredFields(parser, builder)"));
        assertThat(content.lines().filter(line -> line.contains(" builder = ")).count(), is(1L));
    }

    @Test
    void selectsDistinctPrefixesAndHandlesOverlappingSmallSchemas() throws IOException {
        var result = compile("Prefixes.java", """
                package io.helidon.json.tests.generated;

                import io.helidon.json.binding.Json;

                @SuppressWarnings("helidon:api:preview")
                public class Prefixes {
                    @Json.Entity
                    public record Different(int id, int idExtra, int name, int status, int userId, int revision, int requestId,
                                            int displayName, int age, int active, int verified, int priority) {
                    }

                    @Json.Entity
                    public record SingleByte(int account, int balance, int city, int date, int email, int fee, int groupId,
                                             int host, int id, int job, int kind, int label, int mode, int note, int owner) {
                    }

                    @Json.Entity
                    public record Shared(int id, int user, int userId, int unit, int unitId, int qty, int cost, int total) {
                    }

                    @Json.Entity
                    public record FourBytes(int abc, int abcExtra, int other) {
                    }

                    @Json.Entity
                    public record EmptyFirst(@Json.Property("") int empty, int alpha, int beta) {
                    }

                    @Json.Entity
                    public record OneCharacter(int a, int aExtra, int beta) {
                    }
                }
                """);

        assertCompilationSuccess(result);
        String different = Files.readString(generatedSource(result, "Prefixes_Different__GeneratedConverter.java"));
        String singleByte = Files.readString(generatedSource(result, "Prefixes_SingleByte__GeneratedConverter.java"));
        String shared = Files.readString(generatedSource(result, "Prefixes_Shared__GeneratedConverter.java"));
        String fourBytes = Files.readString(generatedSource(result, "Prefixes_FourBytes__GeneratedConverter.java"));
        String empty = Files.readString(generatedSource(result, "Prefixes_EmptyFirst__GeneratedConverter.java"));
        String one = Files.readString(generatedSource(result, "Prefixes_OneCharacter__GeneratedConverter.java"));
        assertThat(different, containsString("switch ((int) first & 16777215)"));
        assertThat(different, containsString("case 0x226469 -> 0;"));
        assertThat(singleByte, containsString("return PROPERTY_NAMES_TRANSITIONS["));
        assertThat(shared, not(containsString("switch ((int) first & 16777215)")));
        assertThat(shared, containsString("int route = PROPERTY_NAMES_TRANSITIONS["));
        assertThat(shared, containsString("switch (route)"));
        assertThat(fourBytes, containsString("switch ((int) first)"));
        assertThat(fourBytes, containsString("case 0x22636261 -> 0;"));
        assertThat(empty, containsString("switch ((int) first & 255)"));
        assertThat(empty, containsString("case 0x22 -> 0;"));
        assertThat(one, containsString("switch ((int) first & 65535)"));
        assertThat(one, containsString("case 0x2261 -> 0;"));
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
            assertOrdinalCases(content, 1, List.of("URL_1"), List.of("case URL_1 -> { // \"url\""));
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
        assertOrdinalCases(content, 2,
                          List.of("SAMPLE"),
                          List.of("case SAMPLE -> {"));
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
        assertOrdinalCases(content, 2,
                          List.of("MY_KEY", "MY_KEY_1", "POLYMORPHIC_ALIASES_1", "COMMENT__TAIL__SUFFIX"),
                          List.of("case MY_KEY -> { // \"my-key\"",
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
        assertOrdinalCases(content, 1,
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

    private static void assertOrdinalCases(String content, int expectedSwitches, List<String> names, List<String> cases) {
        List<String> dispatchCases = new ArrayList<>();
        int switchIndent = -1;
        int checked = 0;
        for (String line : content.lines().toList()) {
            String text = line.strip();
            int indent = line.length() - line.stripLeading().length();
            if (text.endsWith("switch (property) {") || text.endsWith("switch (alias) {")) {
                dispatchCases.clear();
                switchIndent = indent;
            } else if (indent == switchIndent && text.startsWith("}")) {
                assertThat(dispatchCases, is(cases));
                checked++;
                switchIndent = -1;
            } else if (switchIndent >= 0 && text.startsWith("case ")) {
                dispatchCases.add(text);
            }
        }
        assertThat(checked, is(expectedSwitches));
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
