/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
        assertThat(HttpMediaType.create("type/subtype; o1=").parameters(), is(Map.of()));
        assertThat(HttpMediaType.create("type/subtype; o1=; o2=v2").parameters(), is(Map.of("o2", "v2")));
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
        assertThrows(IllegalArgumentException.class, () -> {
                         HttpMediaType.create("text");
                     },
                     "Cannot parse media type: text");
    }

    // Calling create method with "text" argument shall return "text/plain" in relaxed mode.
    @Test
    void parseInvalidTextInRelaxedMode() {
        HttpMediaType type = HttpMediaType.create("text", ParserMode.RELAXED);
        assertThat(type.text(), is("text/plain"));
    }

}
