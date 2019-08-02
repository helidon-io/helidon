/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package io.helidon.openapi;

import java.util.List;

import io.helidon.common.CollectionsHelper;
import io.helidon.openapi.ParameterParser.Location;
import io.helidon.openapi.ParameterParser.Style;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the OpenAPI parameter utility methods.
 * <p>
 * Note that we test the parse method that accepts a List only for the simple
 * style. That's because a single header value can occur multiple times in the
 * request, so getting all of them gives a List of Strings. And the header location
 * allows only the simple style.
 */
public class ParameterParserTest {

    public ParameterParserTest() {
    }

    @Test
    public void testSimpleStyle() {
        final ParameterParser parser = ParameterParser.builder("id", Location.PATH)
                .build(); // uses default SIMPLE style

        final String input = "a,b,c";
        final List<String> expected = CollectionsHelper.listOf("a,b,c".split(","));

        final List<String> result = parser.parse(input);
        assertEquals(expected, result);

        final ParameterParser headerParser = ParameterParser.builder("id", Location.HEADER)
                .build(); // uses the default and only possible style: SIMPLE

        final List<String> listInput = CollectionsHelper.listOf("a,b,c", "d,e");
        final List<String> headerExpected = CollectionsHelper.listOf("a,b,c,d,e".split(","));

        final List<String> headerResult = headerParser.parse(listInput);
        assertEquals(headerExpected, headerResult);
    }

    @Test
    public void testLabelStyle() {
        final ParameterParser parser = ParameterParser.builder("id", Location.PATH)
                .style(Style.LABEL)
                .build();

        final String input = ".a,b,c";
        final List<String> expected = CollectionsHelper.listOf("a,b,c".split(","));

        assertEquals(expected, parser.parse(input));

        final String explodedInput = ".a.b.c";
        final ParameterParser explodedParser = ParameterParser.builder("id", Location.PATH)
                .style(Style.LABEL)
                .exploded(true)
                .build();

        assertEquals(expected, explodedParser.parse(explodedInput));

    }

    @Test
    public void testMatrixStyle() {
        final ParameterParser parser = ParameterParser.builder("id", Location.PATH)
                .style(Style.MATRIX)
                .build();

        final String input = ";id=a,b,c";
        final List<String> expected = CollectionsHelper.listOf("a,b,c".split(","));

        assertEquals(expected, parser.parse(input));

        final ParameterParser explodedParser = ParameterParser.builder("id", Location.PATH)
                .style(Style.MATRIX)
                .exploded(true)
                .build();

        final String explodedInput = ";id=a;id=b;id=c";
        assertEquals(expected, explodedParser.parse(explodedInput));

        assertThrows(IllegalArgumentException.class, () -> {
            ParameterParser.builder("id", Location.PATH)
                    .style(Style.FORM) // path does not support form
                    .build();
        });
    }

    @Test
    public void testFormStyle() {
        final ParameterParser parser = ParameterParser.builder("id", Location.COOKIE)
                .exploded(false)
                .build(); // uses the default and only style for cookie: FORM

        final String input = "id=a&b&c";
        final List<String> expected = CollectionsHelper.listOf("a,b,c".split(","));

        assertEquals(expected, parser.parse(input));

        final String explodedInput = "id=a&id=b&id=c";
        final ParameterParser explodedParser = ParameterParser.builder("id", Location.COOKIE)
                .exploded(true)
                .build();
        assertEquals(expected, explodedParser.parse(explodedInput));
    }

    @Test
    public void testSpaceDelimitedStyle() {
        ParameterParser parser = ParameterParser.builder("id", Location.QUERY)
                .style(Style.SPACE_DELIMITED)
                .exploded(false)
                .build();

        String input = "a%20b%20c";
        final List<String> expected = CollectionsHelper.listOf("a,b,c".split(","));

        assertEquals(expected, parser.parse(input));


    }

    @Test
    public void testPipeDelimitedStyle() {
        ParameterParser parser = ParameterParser.builder("id", Location.QUERY)
                .style(Style.PIPE_DELIMITED)
                .exploded(false)
                .build();

        final String input = "a|b|c";

        assertEquals(CollectionsHelper.listOf("a,b,c".split(",")), parser.parse(input));
    }

    @Test
    public void testLocationValidation() {

        assertThrows(IllegalArgumentException.class, () -> {
            Location.match("badOnPurpose");
        });
    }

    @Test
    public void testPathStyles() {
        testGoodAndBadStylesForLocation("path",
                new String[] {"simple", "label", "matrix"},
                new String[] {"form", "spaceDelimited", "pipeDelimited", "deepObject"});
    }

    @Test
    public void testQueryStyles() {
        testGoodAndBadStylesForLocation("query",
                new String[] {"form", "spaceDelimited", "pipeDelimited", "deepObject"},
                new String[] {"simple", "label", "matrix"});
    }

    @Test
    public void testHeaderStyles() {
        testGoodAndBadStylesForLocation("header",
                new String[] {"simple"},
                new String[] {"label", "matrix", "form", "spaceDelimited", "pipeDelimited", "deepObject"});
    }

    @Test
    public void testCookieStyles() {
        testGoodAndBadStylesForLocation("cookie",
                new String[] {"form"},
                new String[] {"simple, spaceDelimited", "pipeDelimited", "deepObject"});
    }

    private void testGoodAndBadStylesForLocation(String location,
            String[] goodStyles,
            String[] badStyles) {

        for (String style : goodStyles) {
            ParameterParser.builder("id", Location.match(location))
                    .style(Style.match(style));
        }

        for (String style : badStyles) {
            assertThrows(IllegalArgumentException.class, () -> {
                // The style is not valid for this location.
                ParameterParser.builder("id", Location.match(location))
                        .style(Style.match(style));
            });
        }
    }
}
