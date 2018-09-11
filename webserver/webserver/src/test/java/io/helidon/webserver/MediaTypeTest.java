/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.util.Map;
import java.util.Optional;

import io.helidon.common.http.MediaType;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

import static io.helidon.webserver.utils.TestUtils.matcher;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.number.IsCloseTo.closeTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The MediaTypeTest.
 */
public class MediaTypeTest {

    @Test
    public void parseEquals() throws Exception {
        assertEquals(MediaType.APPLICATION_JSON, MediaType.parse("application/json"));
    }

    @Test
    public void parseIdentity() throws Exception {
        assertTrue(MediaType.APPLICATION_JSON == MediaType.parse("application/json"));
    }

    @Test
    public void parseUnknownType() throws Exception {
        MediaType mediaType = MediaType.parse("unknown-type/unknown-subtype");

        assertThat(mediaType.getType(), Is.is("unknown-type"));
        assertThat(mediaType.getSubtype(), Is.is("unknown-subtype"));
        assertThat(mediaType.getCharset(), Is.is(Optional.empty()));
        assertThat(mediaType.getParameters().entrySet(), iterableWithSize(0));

    }

    @Test
    public void parseCharset() throws Exception {
        MediaType mediaType = MediaType.parse("unknown-type/unknown-subtype; charset=utf-8");

        assertThat(mediaType.getCharset().get(), Is.is("utf-8"));
        assertThat(mediaType.getParameters().entrySet(), allOf(
                iterableWithSize(1),
                contains(allOf(
                        matcher(Map.Entry::getKey, Is.is("charset")),
                        matcher(Map.Entry::getValue, Is.is("utf-8"))))));
    }

    @Test
    public void parseParameters() throws Exception {
        MediaType mediaType = MediaType.parse("unknown-type/unknown-subtype; option1=value1; option2=value2");

        assertThat(mediaType.getParameters().entrySet(), allOf(
                iterableWithSize(2),
                contains(
                        allOf(
                                matcher(Map.Entry::getKey, Is.is("option1")),
                                matcher(Map.Entry::getValue, Is.is("value1"))),
                        allOf(
                                matcher(Map.Entry::getKey, Is.is("option2")),
                                matcher(Map.Entry::getValue, Is.is("value2"))))));
    }

    @Test
    public void parseDuplicateParameters() throws Exception {
        MediaType mediaType = MediaType.parse("unknown-type/unknown-subtype; option=value1; option=value2");

        assertThat(mediaType.getParameters().entrySet(), allOf(
                iterableWithSize(1),
                contains(matcher(Map.Entry::getKey, Is.is("option")))));
    }

    @Test
    public void qualityFactor() throws Exception {
        MediaType mediaType = MediaType.parse("unknown-type/unknown-subtype; q=0.2");

        assertThat(mediaType.qualityFactor(), closeTo(0.2, 0.000001));
    }

    @Test
    public void asPredicate() throws Exception {
        assertTrue(MediaType.parse("application/json").test(MediaType.APPLICATION_JSON));
        assertTrue(MediaType.APPLICATION_JSON.test(MediaType.parse("application/json")));
        assertTrue(MediaType.parse("application/*").test(MediaType.APPLICATION_JSON));
        assertTrue(MediaType.APPLICATION_JSON.test(MediaType.parse("application/*")));

        assertTrue(MediaType.parse("application/json").test(MediaType.APPLICATION_JSON.withCharset("UTF-8")));
        assertTrue(MediaType.APPLICATION_JSON.withCharset("UTF-8").test(MediaType.parse("application/json")));
    }

    @Test
    public void jsonPredicate() throws Exception {
        assertTrue(MediaType.JSON_PREDICATE.test(MediaType.parse("application/json")));
        assertTrue(MediaType.JSON_PREDICATE.test(MediaType.parse("application/javascript")));
        assertTrue(MediaType.JSON_PREDICATE.test(MediaType.parse("application/manifest+json")));
        assertFalse(MediaType.JSON_PREDICATE.test(MediaType.parse("application/manifest")));
    }
}
