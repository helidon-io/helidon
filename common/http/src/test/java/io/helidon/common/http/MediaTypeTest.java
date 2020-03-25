/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.http;

import java.util.Map;
import java.util.Optional;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize;
import static org.hamcrest.number.IsCloseTo.closeTo;

/**
 * Unit test for {@link MediaType}.
 */
class MediaTypeTest {
    @Test
    void parseEquals() {
        assertThat(MediaType.parse("application/json"), is(MediaType.APPLICATION_JSON));
    }

    @Test
    void parseIdentity() {
        assertThat(MediaType.parse("application/json"), sameInstance(MediaType.APPLICATION_JSON));
    }

    @Test
    public void parseUnknownType() {
        MediaType mediaType = MediaType.parse("unknown-type/unknown-subtype");

        assertThat(mediaType.type(), Is.is("unknown-type"));
        assertThat(mediaType.subtype(), Is.is("unknown-subtype"));
        assertThat(mediaType.charset(), Is.is(Optional.empty()));
        assertThat(mediaType.parameters().entrySet(), iterableWithSize(0));
    }

    @Test
    void parseCharset() {
        MediaType mediaType = MediaType.parse("unknown-type/unknown-subtype; charset=utf-8");

        assertThat(mediaType.charset(), not(Optional.empty()));
        assertThat(mediaType.charset().get(), is("utf-8"));
        assertThat(mediaType.parameters(), is(Map.of("charset", "utf-8")));
    }

    @Test
    void parseParameters() {
        MediaType mediaType = MediaType.parse("unknown-type/unknown-subtype; option1=value1; option2=value2");

        assertThat(mediaType.parameters(), is(Map.of("option1", "value1",
                                                                      "option2", "value2")));
    }

    @Test
    void parseDuplicateParameters() {
        MediaType mediaType = MediaType.parse("unknown-type/unknown-subtype; option=value1; option=value2");

        assertThat(mediaType.parameters(), is(Map.of("option", "value2")));
    }

    @Test
    void qualityFactor() {
        MediaType mediaType = MediaType.parse("unknown-type/unknown-subtype; q=0.2");

        assertThat(mediaType.qualityFactor(), closeTo(0.2, 0.000001));
    }

    @Test
    void asPredicate() {
        assertThat(MediaType.parse("application/json").test(MediaType.APPLICATION_JSON), is(true));
        assertThat(MediaType.APPLICATION_JSON.test(MediaType.parse("application/json")), is(true));
        assertThat(MediaType.parse("application/*").test(MediaType.APPLICATION_JSON), is(true));
        assertThat(MediaType.APPLICATION_JSON.test(MediaType.parse("application/*")), is(true));

        assertThat(MediaType.parse("application/json").test(MediaType.APPLICATION_JSON.withCharset("UTF-8")), is(true));
        assertThat(MediaType.APPLICATION_JSON.withCharset("UTF-8").test(MediaType.parse("application/json")), is(true));
    }

    @Test
    void jsonPredicate() {
        assertThat(MediaType.JSON_PREDICATE.test(MediaType.parse("application/json")), is(true));
        assertThat(MediaType.JSON_PREDICATE.test(MediaType.parse("application/javascript")), is(false));
        assertThat(MediaType.JSON_PREDICATE.test(MediaType.parse("application/manifest+json")), is(true));
        assertThat(MediaType.JSON_PREDICATE.test(MediaType.parse("application/manifest")), is(false));
    }

    @Test
    void testText() {
        MediaType textPlain = MediaType.TEXT_PLAIN;

        assertThat(textPlain.type(), is("text"));
        assertThat(textPlain.subtype(), is("plain"));
        assertThat(textPlain.charset(), is(Optional.empty()));
    }

    @Test
    void testBuilt() {
        MediaType mediaType = MediaType.builder()
                .type("application")
                .subtype("json")
                .charset("ISO-8859-2")
                .addParameter("q", "0.1")
                .build();

        assertThat(mediaType.type(), is("application"));
        assertThat(mediaType.subtype(), is("json"));
        assertThat(mediaType.charset(), is(Optional.of("ISO-8859-2")));
        assertThat(mediaType.parameters(), is(Map.of("q", "0.1", "charset", "ISO-8859-2")));
        assertThat(mediaType.qualityFactor(), closeTo(0.1, 0.000001));
    }
}