/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.media.type.ParserMode;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize;
import static org.hamcrest.number.IsCloseTo.closeTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link MediaType}.
 */
class MediaTypeTest {
    @Test
    public void parseUnknownType() {
        HttpMediaType mediaType = HttpMediaType.create("unknown-type/unknown-subtype");

        assertThat(mediaType.mediaType().type(), is("unknown-type"));
        assertThat(mediaType.mediaType().subtype(), is("unknown-subtype"));
        assertThat(mediaType.charset(), is(Optional.empty()));
        assertThat(mediaType.parameters().entrySet(), iterableWithSize(0));
    }

    @Test
    void parseEquals() {
        assertThat(HttpMediaType.create("application/json").mediaType(), is(MediaTypes.APPLICATION_JSON));
    }

    @Test
    void parseIdentity() {
        assertThat(HttpMediaType.create("application/json").mediaType(), sameInstance(MediaTypes.APPLICATION_JSON));
    }

    @Test
    void parseCharset() {
        HttpMediaType mediaType = HttpMediaType.create("unknown-type/unknown-subtype; charset=utf-8");

        assertThat(mediaType.charset(), not(Optional.empty()));
        assertThat(mediaType.charset().get(), is("utf-8"));
        assertThat(mediaType.parameters(), is(Map.of("charset", "utf-8")));
    }

    @Test
    void parseParameters() {
        HttpMediaType mediaType = HttpMediaType.create("unknown-type/unknown-subtype; option1=value1; option2=value2");

        assertThat(mediaType.parameters(), is(Map.of("option1", "value1",
                                                     "option2", "value2")));
    }

    @Test
    void parseDuplicateParameters() {
        HttpMediaType mediaType = HttpMediaType.create("unknown-type/unknown-subtype; option=value1; option=value2");

        assertThat(mediaType.parameters(), is(Map.of("option", "value2")));
    }

    @Test
    void parseEmptyParameterValue() {
        assertThrows(IllegalArgumentException.class, () -> HttpMediaType.create("type/subtype; o1="));
        assertThrows(IllegalArgumentException.class, () -> HttpMediaType.create("type/subtype; o1=; o2=v2"));

        assertThat(HttpMediaType.create("type/subtype; o1=", ParserMode.RELAXED).parameters(), is(Map.of()));
        assertThat(HttpMediaType.create("type/subtype; o1=; o2=v2", ParserMode.RELAXED).parameters(),
                   is(Map.of("o2", "v2")));
    }

    @Test
    void parseQuotedParameterValue() {
        HttpMediaType mediaType = HttpMediaType.create("text/plain; profile=\"a;b\\\"c\\\\d\"; empty=\"\"");

        assertThat(mediaType.parameters(), is(Map.of("profile", "a;b\"c\\d", "empty", "")));
        assertThat(mediaType.text(), is("text/plain; empty=\"\"; profile=\"a;b\\\"c\\\\d\""));
    }

    @Test
    void parseQuotedParameterValueInRelaxedMode() {
        HttpMediaType mediaType = HttpMediaType.create("text/plain; profile=\"a;b\"", ParserMode.RELAXED);

        assertThat(mediaType.parameters(), is(Map.of("profile", "a;b")));
    }

    @Test
    void parseQuotedPairCharacters() {
        HttpMediaType escaped = HttpMediaType.create("text/plain; value=\"a\\~\\" + (char) 0x80 + "\"");
        HttpMediaType unescaped = HttpMediaType.create("text/plain; value=\"a" + (char) 0x80 + "\"");

        assertThat(escaped.parameters(), is(Map.of("value", "a~" + (char) 0x80)));
        assertThat(unescaped.parameters(), is(Map.of("value", "a" + (char) 0x80)));
    }

    @Test
    void parseOptionalWhitespaceAndEmptyParameters() {
        HttpMediaType mediaType = HttpMediaType.create("text/plain\t; ; Charset=\"utf-8\";;format=flowed;");

        assertThat(mediaType.parameters(), is(Map.of("charset", "utf-8", "format", "flowed")));
        assertThat(mediaType.text(), is("text/plain; charset=utf-8; format=flowed"));
    }

    @Test
    void rejectInvalidMediaTypes() {
        List<String> invalidMediaTypes = List.of("",
                                                 "text",
                                                 "/plain",
                                                 "text/",
                                                 "text//plain",
                                                 " text/plain",
                                                 "text/plain ",
                                                 "text /plain",
                                                 "text/ plain",
                                                 "text/plain, application/json",
                                                 "text/plain; =value",
                                                 "text/plain; name =value",
                                                 "text/plain; name= value",
                                                 "text/plain; name=",
                                                 "text/plain; name=;",
                                                 "text/plain; name=one two",
                                                 "text/plain; name=\"unterminated",
                                                 "text/plain; name=\"value\"x",
                                                 "text/plain; name=\"bad" + (char) 0 + "value\"",
                                                 "text/plain; name=\"bad" + (char) 0x7f + "value\"",
                                                 "text/plain; name=\"bad" + (char) 0x100 + "value\"",
                                                 "text/plain; name=\"bad\\" + (char) 0 + "value\"",
                                                 "text/plain; name=\"bad\\" + (char) 0x7f + "value\"");

        invalidMediaTypes.forEach(value -> assertThrows(IllegalArgumentException.class,
                                                        () -> HttpMediaType.create(value),
                                                        value));
    }

    @Test
    void serializeParameterValuesUsingRfc9110Syntax() {
        HttpMediaType mediaType = HttpMediaType.builder()
                .mediaType(MediaTypes.TEXT_PLAIN)
                .addParameter("token", "value")
                .addParameter("quoted", "a b;c\"d\\e")
                .build();

        assertThat(mediaType.text(), is("text/plain; quoted=\"a b;c\\\"d\\\\e\"; token=value"));
    }

    @Test
    void qualityFactor() {
        HttpMediaType mediaType = HttpMediaType.create("unknown-type/unknown-subtype; q=0.2");

        assertThat(mediaType.qualityFactor(), closeTo(0.2, 0.000001));
    }

    @Test
    void asPredicate() {
        assertThat(HttpMediaType.create("application/json").test(MediaTypes.APPLICATION_JSON), is(true));
        assertThat(HttpMediaTypes.JSON_UTF_8.test(MediaTypes.create("application/json")), is(true));
        assertThat(HttpMediaType.create("application/*").test(MediaTypes.APPLICATION_JSON), is(true));
        assertThat(HttpMediaTypes.JSON_UTF_8.test(MediaTypes.create("application/*")), is(true));

        assertThat(HttpMediaType.create(MediaTypes.APPLICATION_JSON).withCharset("UTF-8")
                           .test(MediaTypes.create("application/json")), is(true));
    }

    @Test
    void jsonPredicate() {
        assertThat(HttpMediaTypes.JSON_PREDICATE.test(HttpMediaType.create(MediaTypes.create("application/json"))), is(true));
        assertThat(HttpMediaTypes.JSON_PREDICATE.test(HttpMediaType.create(MediaTypes.create("application/javascript"))),
                   is(false));
        assertThat(HttpMediaTypes.JSON_PREDICATE.test(HttpMediaType.create(MediaTypes.create("application/manifest+json"))),
                   is(true));
        assertThat(HttpMediaTypes.JSON_PREDICATE.test(HttpMediaType.create(MediaTypes.create("application/manifest"))), is(false));
    }

    @Test
    void testText() {
        HttpMediaType textPlain = HttpMediaType.create(MediaTypes.TEXT_PLAIN);

        assertThat(textPlain.mediaType(), is(MediaTypes.TEXT_PLAIN));
        assertThat(textPlain.charset(), is(Optional.empty()));
    }

    @Test
    void testBuilt() {
        HttpMediaType mediaType = HttpMediaType.builder()
                .mediaType(MediaTypes.create("application", "json"))
                .charset("ISO-8859-2")
                .addParameter("q", "0.1")
                .build();

        assertThat(mediaType.mediaType(), is(MediaTypes.create("application", "json")));
        assertThat(mediaType.charset(), is(Optional.of("ISO-8859-2")));
        assertThat(mediaType.parameters(), is(Map.of("q", "0.1", "charset", "ISO-8859-2")));
        assertThat(mediaType.qualityFactor(), closeTo(0.1, 0.000001));
    }

    // Calling create method with "text" argument shall throw IllegalArgumentException in strict mode.
    @Test
    void parseInvalidTextInStrictMode() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                           () -> HttpMediaType.create("text"));

        assertThat(exception.getMessage(), is("Cannot parse media type: text"));
    }

    @Test
    void rejectNullParserMode() {
        assertThrows(NullPointerException.class, () -> HttpMediaType.create("text/plain", null));
    }

    // Calling create method with "text" argument shall return "text/plain" in relaxed mode.
    @Test
    void parseInvalidTextInRelaxedMode() {
        HttpMediaType type = HttpMediaType.create("text", ParserMode.RELAXED);
        assertThat(type.text(), is("text/plain"));
    }

}
