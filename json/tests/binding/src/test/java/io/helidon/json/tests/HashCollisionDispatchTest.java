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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import io.helidon.json.JsonException;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonStringLexer;
import io.helidon.json.JsonValue;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.smile.SmileParser;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
class HashCollisionDispatchTest {
    // FNV-1a("project") == FNV-1a("unknown-IpUce6").
    private static final String COLLIDING_PROPERTY = "{\"unknown-IpUce6\":\"payments\"}";

    private final JsonBinding jsonBinding;

    HashCollisionDispatchTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void rejectsCollidingUnknownProperty(BindingMethod method) {
        JsonException exception = assertThrows(JsonException.class,
                                              () -> method.deserialize(jsonBinding, COLLIDING_PROPERTY, StrictRequest.class));
        assertThat(exception.getMessage(), containsString("Unknown properties"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void rejectsCollidingUnknownOptionalProperty(BindingMethod method) {
        assertThrows(JsonException.class,
                     () -> method.deserialize(jsonBinding, COLLIDING_PROPERTY, StrictOptionalRequest.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void collisionDoesNotSatisfyRequiredProperty(BindingMethod method) {
        JsonException exception = assertThrows(JsonException.class,
                                              () -> method.deserialize(jsonBinding, COLLIDING_PROPERTY, RequiredRequest.class));
        assertThat(exception.getMessage(), containsString("project"));
        assertThat(exception.getMessage(), containsString("missing"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void ignoresCollidingUnknownProperty(BindingMethod method) {
        OptionalRequest request = method.deserialize(jsonBinding, COLLIDING_PROPERTY, OptionalRequest.class);
        assertThat(request.project(), nullValue());
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void collisionDoesNotOverwriteKnownProperty(BindingMethod method) {
        String json = "{\"project\":\"original\",\"unknown-IpUce6\":\"replacement\"}";
        RequiredRequest request = method.deserialize(jsonBinding, json, RequiredRequest.class);
        assertThat(request.project(), is("original"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void skipsCollidingStructuredValueAndContinues(BindingMethod method) {
        String json = "{\"unknown-IpUce6\":{\"nested\":[1,2]},\"project\":\"payments\"}";
        RequiredRequest request = method.deserialize(jsonBinding, json, RequiredRequest.class);
        assertThat(request.project(), is("payments"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void rejectsCollisionWithManyProperties(BindingMethod method) {
        assertThrows(JsonException.class,
                     () -> method.deserialize(jsonBinding, COLLIDING_PROPERTY, ManyProperties.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void readsKnownPropertyWithManyProperties(BindingMethod method) {
        ManyProperties request = method.deserialize(jsonBinding, "{\"project\":\"payments\",\"ninth\":9}",
                                                    ManyProperties.class);
        assertThat(request.project(), is("payments"));
        assertThat(request.ninth(), is(9));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void readsEscapedKnownProperty(BindingMethod method) {
        StrictRequest request = method.deserialize(jsonBinding, "{\"pro\\u006aect\":\"payments\"}", StrictRequest.class);
        assertThat(request.project(), is("payments"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void rejectsEscapedCollidingProperty(BindingMethod method) {
        String json = "{\"unknown-IpUce\\u0036\":\"payments\"}";
        assertThrows(JsonException.class, () -> method.deserialize(jsonBinding, json, StrictRequest.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void readsUnicodeProperty(BindingMethod method) {
        UnicodeRequest expected = new UnicodeRequest("payments");
        assertThat(method.deserialize(jsonBinding, "{\"caf\u00e9\":\"payments\"}", UnicodeRequest.class), is(expected));
        assertThat(method.deserialize(jsonBinding, "{\"caf\\u00e9\":\"payments\"}", UnicodeRequest.class), is(expected));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void rejectsCollidingDiscriminator(BindingMethod method) {
        // FNV-1a("costarring") == FNV-1a("liquid").
        String json = "{\"liquid\":\"project\",\"value\":\"payments\"}";
        JsonException exception = assertThrows(JsonException.class,
                                              () -> method.deserialize(jsonBinding, json, PolymorphicRequest.class));
        assertThat(exception.getMessage(), containsString("Missing property containing alias"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void rejectsCollidingAlias(BindingMethod method) {
        String json = "{\"costarring\":\"unknown-IpUce6\",\"value\":\"payments\"}";
        JsonException exception = assertThrows(JsonException.class,
                                              () -> method.deserialize(jsonBinding, json, PolymorphicRequest.class));
        assertThat(exception.getMessage(), containsString("Unknown alias value"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void readsEscapedDiscriminatorAndAlias(BindingMethod method) {
        String json = "{\"costar\\u0072ing\":\"pro\\u006aect\",\"value\":\"payments\"}";
        assertThat(method.deserialize(jsonBinding, json, PolymorphicRequest.class), is(new ProjectRequest("payments")));
    }

    @ParameterizedTest
    @EnumSource(ParserInput.class)
    void rejectsCollidingPropertyWithAlternateParser(ParserInput input) {
        JsonParser parser = input.parser(COLLIDING_PROPERTY, jsonBinding);
        assertThrows(JsonException.class, () -> jsonBinding.deserialize(parser, StrictRequest.class));
    }

    @ParameterizedTest
    @EnumSource(ParserInput.class)
    void skipsCollidingPropertyWithAlternateParser(ParserInput input) {
        String json = "{\"unknown-IpUce6\":[1,2],\"project\":\"payments\"}";
        RequiredRequest request = jsonBinding.deserialize(input.parser(json, jsonBinding), RequiredRequest.class);
        assertThat(request.project(), is("payments"));
    }

    @ParameterizedTest
    @EnumSource(ParserInput.class)
    void rejectsCollidingAliasWithAlternateParser(ParserInput input) {
        JsonParser parser = input.parser("{\"costarring\":\"unknown-IpUce6\",\"value\":\"payments\"}", jsonBinding);
        assertThrows(JsonException.class, () -> jsonBinding.deserialize(parser, PolymorphicRequest.class));
    }

    @ParameterizedTest
    @EnumSource(ParserInput.class)
    void readsKnownNamesWithAlternateParser(ParserInput input) {
        String json = "{\"costar\\u0072ing\":\"pro\\u006aect\",\"value\":\"payments\"}";
        assertThat(jsonBinding.deserialize(input.parser(json, jsonBinding), PolymorphicRequest.class),
                   is(new ProjectRequest("payments")));
    }

    @ParameterizedTest
    @EnumSource(ParserInput.class)
    void readsNestedPropertyHeadersWithAlternateParsers(ParserInput input) {
        JsonParser parser = input.parser("{\"child\":{\"value\":1},\"extra\":[1],\"value\":2}", jsonBinding);
        JsonStringLexer names = JsonStringLexer.create("child", "value");
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readProperty(names), is(0));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readProperty(names), is(1));
        assertThat(parser.readInt(), is(1));
        assertThat(parser.nextProperty(names), is(JsonParser.END_OBJECT));
        assertThat(parser.nextProperty(names), is(-1));
        parser.skip();
        assertThat(parser.nextProperty(names), is(1));
        assertThat(parser.readInt(), is(2));
        assertThat(parser.nextProperty(names), is(JsonParser.END_OBJECT));
    }

    @ParameterizedTest
    @EnumSource(ParserInput.class)
    void advancesAfterReadingACompleteContainer(ParserInput input) {
        String document = "{\"child\":{\"value\":7},\"list\":[3],\"node\":{\"value\":9},\"value\":2}";
        JsonParser parser = input.parser(document, jsonBinding);
        JsonStringLexer names = JsonStringLexer.create("child", "list", "node", "value");
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readProperty(names), is(0));
        assertThat(parser.readJsonObject(), is(JsonParser.create("{\"value\":7}").readJsonObject()));
        assertThat(parser.nextProperty(names), is(1));
        assertThat(parser.readJsonArray(), is(JsonParser.create("[3]").readJsonArray()));
        assertThat(parser.nextProperty(names), is(2));
        assertThat(parser.readJsonValue(), is(JsonParser.create("{\"value\":9}").readJsonValue()));
        assertThat(parser.nextProperty(names), is(3));
        assertThat(parser.readInt(), is(2));
        assertThat(parser.nextProperty(names), is(JsonParser.END_OBJECT));
    }

    @ParameterizedTest
    @EnumSource(ParserInput.class)
    void routesSharedBlocksAndSupplementaryCharacters(ParserInput input) {
        String json = "{\"abcdefg\\uD83D\\uDE01\":2,\"abcdefgh\":3,\"abcdefg😀\":1,\"abcdefg😁x\":9}";
        assertThat(jsonBinding.deserialize(input.parser(json, jsonBinding), SharedNames.class),
                   is(new SharedNames(1, 2, 3)));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void distinguishesRawAndEscapedInternationalNames(BindingMethod method) {
        String json = "{\"cafe\\u0301\":2,\"設定前置\\u4e59\":4,\"café\":1,\"設定前置甲\":3,"
                + "\"設定前置丙\":90,\"caféx\":91}";
        assertThat(method.deserialize(jsonBinding, json, InternationalNames.class), is(new InternationalNames(1, 2, 3, 4)));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void verifiesTheWholeNameAfterSelectingASmallAsciiPrefix(BindingMethod method) {
        String json = "{\"priority\":12,\"verified\":11,\"priorities\":90,\"created\\u0041t\":9,"
                + "\"priorityExtra\":91,\"name\":2,\"id\":1,\"verifiedX\":92}";
        assertThat(method.deserialize(jsonBinding, json, ContactFields.class),
                   is(new ContactFields(1, 2, 0, 0, 0, 0, 0, 0, 9, 0, 11, 12)));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void matchesShortAndLongNamesUsingBothInputWords(BindingMethod method) {
        String json = "{\"prefix04ab\":12,\"prefix04abx\":98,\"ab\"      :2,\"prefix01aa\":5,"
                + "\"prefix03aa\":9,\"prefix00ab\":4,\"prefix02ab\":8,\"a\" :1,\"prefix03ab\":10,"
                + "\"prefix01ab\":6,\"prefix04aa\":11,\"prefix02aa\":7,\"prefix00a\\u0061\":3,\"ax\":99,\"prefix05aa\":97}";
        assertThat(method.deserialize(jsonBinding, json, ProjectedNames.class),
                   is(new ProjectedNames(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void matchesAbbreviatedFieldsWithDifferentLengths(BindingMethod method) {
        String doc = "{\"minMs\":7,\"offPeak\":14,\"min\" \t:11,\"max\":1,\"inMs\":8,\"minId\":5,\"in\":4,\"\":13,"
                + "\"countA\":6,\"cpu\\u004dax\":10,\"idMax\":2,\"valMs\":9,\"unitA\":12,\"count\":3,\"off\":15,"
                + "\"minId123456789\":90,\"offPeal\":91,\"min\\\"Ms\":92,\"mínMs\":93,\"q\":94}";
        assertThat(method.deserialize(jsonBinding, doc, ShortMetrics.class),
                   is(new ShortMetrics(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void verifiesThePrefixOfUtf8FieldsSelectedBySuffixLength(BindingMethod method) {
        String doc = "{\"東京offPeak\":14,\"東京minMs\":7,\"東京\" \t:13,\"東京min\":11,\"京都offPeak\":90,"
                + "\"東京offPeax\":91,\"東京minId123456789\":92,\"東京\\u0069nMs\":8}";
        assertThat(method.deserialize(jsonBinding, doc, PrefixedMetrics.class),
                   is(new PrefixedMetrics(0, 0, 0, 0, 0, 0, 7, 8, 0, 0, 11, 0, 13, 14, 0)));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void verifiesNumberedNamesAtTheEndOfAPartialRange(BindingMethod method) {
        String json = "{\"trailingCounter\":42,\"stat0\\u0030k\":10,\"stat0\\u0039k\":19,\"stat1\\u0036k\":26,"
                + "\"stat1\\u0037k\":89,\"stat1\\u0039k\":90,\"stat0\\u003ak\":91,\"stAt09k\":92,\"stat0\\u0139k\":93,"
                + "\"stat0\\u0040k\":94,\"stat09x\":95,\"id\":7}";
        RangeCounters expected = new RangeCounters(7, 10, 0, 0, 0, 0, 0, 0, 0, 0, 19, 0, 0, 0, 0, 0, 0, 26, 42);
        assertThat(method.deserialize(jsonBinding, json, RangeCounters.class), is(expected));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void checksNonzeroDigitOffsetsAndRejectsInternalGaps(BindingMethod method) {
        String json = "{\"leading\":42,\"level\\u0041\":1,\"level\\u0048\":8,\"level\\u0040\":90,\"level\\u0049\":91,"
                + "\"zone0\\u0034x\":4,\"zone0\\u0033x\":93,\"zone0\\u0039x\":94}";
        LetterLevels expected = new LetterLevels(42, 1, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0);
        assertThat(method.deserialize(jsonBinding, json, LetterLevels.class), is(expected));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void distinguishesKnownAndUnknownLongWireNames(BindingMethod method) {
        String json = "{\"shipping.destination.address.postalCode\":1,\"shipping.destination.address.postalMode\":90,"
                + "\"shipping.destination.address.street\\u0041ddress\":2,\"shipping.destination.address.postalCodes\":91}";
        assertThat(method.deserialize(jsonBinding, json, LongFields.class), is(new LongFields(1, 2)));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void resumesAfterAnOrderedPrefixWithoutReplayingSetters(BindingMethod method) {
        String doc = "{\"id\":1,\"label\":\"first\",\"unknown\":{\"label\":\"ignored\"},\"count\":5,\"label\":\"last\"}";
        SetterSequence result = method.deserialize(jsonBinding, doc, SetterSequence.class);
        assertThat(result.id(), is(1));
        assertThat(result.label(), is("last"));
        assertThat(result.count(), is(5));
        assertThat(result.writes(), is("ilcl"));
        String shuffled = "{\"count\":5,\"id\":1,\"label\":\"last\"}";
        assertThat(method.deserialize(jsonBinding, shuffled, SetterSequence.class).writes(), is("cil"));
    }

    @ParameterizedTest
    @EnumSource(ParserInput.class)
    void keepsRequiredFieldsAcrossPartialOrderAndAlternateParsers(ParserInput input) {
        String doc = "{\"id\":8,\"label\":\"first\",\"extra\":[1,2],\"count\":3,\"la\\u0062el\":\"last\"}";
        assertThat(jsonBinding.deserialize(input.parser(doc, jsonBinding), RequiredOrder.class),
                   is(new RequiredOrder(8, "last", 3)));
        JsonParser missing = input.parser("{\"label\":\"only\",\"count\":2}", jsonBinding);
        assertThrows(JsonException.class, () -> jsonBinding.deserialize(missing, RequiredOrder.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void verifiesTheRestOfANameAfterItsPrefixSelectsAField(BindingMethod method) {
        String doc = "{\"idExtra\":2,\"id\" \t:1,\"name\":3,\"status\":4,\"userId\":5,\"revision\":6,"
                + "\"re\\u0071uestId\":7,\"displayName\":8,\"requestIx\":90,\"nam\":91,\"revista\":92,\"idExtraX\":93}";
        assertThat(method.deserialize(jsonBinding, doc, PrefixFields.class), is(new PrefixFields(1, 2, 3, 4, 5, 6, 7, 8)));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void recognizesNumberedFieldsAndTheirExactSuffixes(BindingMethod method) {
        String json = "{\"region35Total\":5,\"r\\u0065gion00Hits\":7,\"region00Total\":88,"
                + "\"region00\\u0148its\":89,\"region35Hits\":90,\"region36Hits\":91,\"region17Tot\\u0061l\":9}";
        RegionMetrics result = method.deserialize(jsonBinding, json, RegionMetrics.class);
        assertThat(result.region00Hits(), is(7));
        assertThat(result.region17Total(), is(9));
        assertThat(result.region35Total(), is(5));
        assertThat(result.region01Total(), is(0));
    }

    enum ParserInput {
        SMALL_STREAM,
        JSON_VALUE,
        SMILE_ARRAY,
        SMILE_STREAM;

        JsonParser parser(String json, JsonBinding binding) {
            if (this == SMALL_STREAM) {
                return JsonParser.create(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), 6);
            }
            JsonValue value = JsonParser.create(json).readJsonValue();
            if (this == JSON_VALUE) {
                return JsonParser.create(value);
            }
            byte[] smile = SmileBindingSupport.serializeSmile(binding, value);
            return this == SMILE_ARRAY ? SmileParser.create(smile) : SmileParser.create(new ByteArrayInputStream(smile), 8);
        }
    }

    @Json.Entity
    @Json.Polymorphic(key = "costarring")
    @Json.Subtype(alias = "project", value = ProjectRequest.class)
    interface PolymorphicRequest {
    }

    @Json.Entity
    @Json.FailOnUnknown
    record StrictRequest(@Json.Required String project) {
    }

    @Json.Entity
    @Json.FailOnUnknown
    record StrictOptionalRequest(String project) {
    }

    @Json.Entity
    record RequiredRequest(@Json.Required String project) {
    }

    @Json.Entity
    record OptionalRequest(String project) {
    }

    @Json.Entity
    @Json.FailOnUnknown
    record ManyProperties(@Json.Required String project, int first, int second, int third, int fourth, int fifth,
                          int sixth, int seventh, int eighth, int ninth) {
    }

    @Json.Entity
    @Json.FailOnUnknown
    record UnicodeRequest(@Json.Property("caf\u00e9") String value) {
    }

    @Json.Entity
    record SharedNames(@Json.Property("abcdefg😀") int first, @Json.Property("abcdefg😁") int second, int abcdefgh) {
    }

    @Json.Entity
    record InternationalNames(@Json.Property("café") int composed,
                              @Json.Property("cafe\u0301") int decomposed,
                              @Json.Property("設定前置甲") int first,
                              @Json.Property("設定前置乙") int second) {
    }

    @Json.Entity
    record ContactFields(int id, int name, int age, int active, int userId, int items, int status, int region,
                         int createdAt, int email, int verified, int priority) {
    }

    @Json.Entity
    record ProjectedNames(int a, int ab, int prefix00aa, int prefix00ab, int prefix01aa, int prefix01ab,
                          int prefix02aa, int prefix02ab, int prefix03aa, int prefix03ab, int prefix04aa, int prefix04ab) {
    }

    @Json.Entity
    record RangeCounters(int id, int stat00k, int stat01k, int stat02k, int stat03k, int stat04k, int stat05k,
                         int stat06k, int stat07k, int stat08k, int stat09k, int stat10k, int stat11k, int stat12k,
                         int stat13k, int stat14k, int stat15k, int stat16k, int trailingCounter) {
    }

    @Json.Entity
    record LetterLevels(int leading, int levelA, int levelB, int levelC, int levelD, int levelE, int levelF, int levelG,
                        int levelH,
                        int zone00x, int zone01x, int zone02x, int zone04x, int zone05x, int zone06x, int zone07x, int zone08x) {
    }

    @Json.Entity
    record LongFields(@Json.Property("shipping.destination.address.postalCode") int postal,
                      @Json.Property("shipping.destination.address.streetAddress") int street) {
    }

    @Json.Entity
    record RequiredOrder(@Json.Required int id, String label, int count) {
    }

    @Json.Entity
    static class SetterSequence {
        private int id;
        private String label;
        private int count;
        private String writes = "";

        public int id() {
            return id;
        }

        public void id(int id) {
            this.id = id;
            writes += "i";
        }

        public String label() {
            return label;
        }

        public void label(String label) {
            this.label = label;
            writes += "l";
        }

        public int count() {
            return count;
        }

        public void count(int count) {
            this.count = count;
            writes += "c";
        }

        String writes() {
            return writes;
        }
    }

    @Json.Entity
    record PrefixFields(int id, int idExtra, int name, int status, int userId, int revision, int requestId, int displayName) {
    }

    @Json.Entity
    record ShortMetrics(int max, int idMax, int count, int in, int minId, int countA, int minMs, int inMs,
                        int valMs, int cpuMax, int min, int unitA, @Json.Property("") int empty, int offPeak, int off) {
    }

    @Json.Entity
    record PrefixedMetrics(@Json.Property("東京max") int max, @Json.Property("東京idMax") int idMax,
                           @Json.Property("東京count") int count, @Json.Property("東京in") int in,
                           @Json.Property("東京minId") int minId, @Json.Property("東京countA") int countA,
                           @Json.Property("東京minMs") int minMs, @Json.Property("東京inMs") int inMs,
                           @Json.Property("東京valMs") int valMs, @Json.Property("東京cpuMax") int cpuMax,
                           @Json.Property("東京min") int min, @Json.Property("東京unitA") int unitA,
                           @Json.Property("東京") int empty, @Json.Property("東京offPeak") int offPeak,
                           @Json.Property("東京off") int off) {
    }

    @Json.Entity
    record RegionMetrics(int region00Hits, int region01Total, int region02Hits, int region03Total,
                         int region04Hits, int region05Total, int region06Hits, int region07Total,
                         int region08Hits, int region09Total, int region10Hits, int region11Total,
                         int region12Hits, int region13Total, int region14Hits, int region15Total,
                         int region16Hits, int region17Total, int region18Hits, int region19Total,
                         int region20Hits, int region21Total, int region22Hits, int region23Total,
                         int region24Hits, int region25Total, int region26Hits, int region27Total,
                         int region28Hits, int region29Total, int region30Hits, int region31Total,
                         int region32Hits, int region33Total, int region34Hits, int region35Total) {
    }

    @Json.Entity
    record ProjectRequest(String value) implements PolymorphicRequest {
    }
}
