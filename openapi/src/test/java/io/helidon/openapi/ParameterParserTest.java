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

import io.helidon.common.CollectionsHelper;
import java.util.List;
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
        ParameterParser parser = ParameterParser.builder()
                .location("path")
                .style("simple")
                .build();

        final String input = "a,b,c";
        List<String> expected = CollectionsHelper.listOf("a,b,c".split(","));

        List<String> result = parser.parse(input);
        assertEquals(expected, result);

        parser = ParameterParser.builder()
                .location("header")
                .style("simple")
                .build();

        final List<String> listInput = CollectionsHelper.listOf("a,b,c", "d,e");
        expected = CollectionsHelper.listOf("a,b,c,d,e".split(","));

        result = parser.parse(listInput);
        assertEquals(expected, result);
    }

    @Test
    public void testLabelStyle() {
        ParameterParser parser = ParameterParser.builder()
                .location("path")
                .style("label")
                .build();

        final String input = ".a.b.c";

        assertEquals(CollectionsHelper.listOf("a,b,c".split(",")), parser.parse(input));
    }

    @Test
    public void testMatrixStyle() {
        ParameterParser parser = ParameterParser.builder()
                .location("path")
                .style("matrix")
                .build();

        final String input = ";a;b;c";

        assertEquals(CollectionsHelper.listOf("a,b,c".split(",")), parser.parse(input));
    }

    @Test
    public void testFormStyle() {
        ParameterParser parser = ParameterParser.builder()
                .location("cookie")
                .style("form")
                .explode(false)
                .build();

        final String input = "a&b&c";

        assertEquals(CollectionsHelper.listOf("a,b,c".split(",")), parser.parse(input));
    }

    @Test
    public void testSpaceDelimitedStyle() {
        ParameterParser parser = ParameterParser.builder()
                .location("query")
                .style("spaceDelimited")
                .explode(false)
                .build();

        final String input = "a b c";

        assertEquals(CollectionsHelper.listOf("a,b,c".split(",")), parser.parse(input));
    }

    @Test
    public void testPipeDelimitedStyle() {
        ParameterParser parser = ParameterParser.builder()
                .location("query")
                .style("pipeDelimited")
                .explode(false)
                .build();

        final String input = "a|b|c";

        assertEquals(CollectionsHelper.listOf("a,b,c".split(",")), parser.parse(input));
    }

    @Test
    public void testLocationValidation() {

        assertThrows(IllegalArgumentException.class, () -> {
            ParameterParser.builder()
                .location("badOnPurpose")
                .build();
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
            ParameterParser.builder()
                .location(location)
                .style(style)
                .build();
        }

        for (String style : badStyles) {
            assertThrows(IllegalArgumentException.class, () -> {
                ParameterParser.builder()
                    .location(location)
                    .style(style) // not valid for this location
                    .build();
            });
        }
    }

//    @Test
//    public void testBadFormat() {
//        assertThrows(IllegalArgumentException.class, () -> {
//                ParameterParser.parse("w$x$y", "dollar");
//            });
//    }
//
//    @Test
//    public void checkAllFormats() {
//        check("ww,xx,yy", "csv", "ww", "xx", "yy");
//        check("aaa bbb ccc", "ssv", "aaa", "bbb", "ccc");
//        check("m\tn\to", "tsv", "m", "n", "o");
//        check("g-g|h-h|i-i|j", "pipes", "g-g", "h-h", "i-i", "j");
//        check("1,2,3", "multi", "1", "2", "3");
//        check("z", "csv", "z");
//    }
//
//    @Test
//    public void checkListOfMultiValues() {
//        final List<String> inputs = new ArrayList<>();
//        inputs.addAll(Arrays.asList("ww,xx,yy", "aaa,bbb,ccc"));
//        checkMulti(inputs, "csv", "ww", "xx", "yy", "aaa", "bbb", "ccc");
//    }
//
//    private void check(String input, String format, String... expected) {
//        final List<String> expectedList = new ArrayList<>();
//        Collections.addAll(expectedList, expected);
//        final List<String> parsed = ParameterParser.parse(input, format);
//        assertEquals(expectedList, parsed, "Unexpected results for format " + format);
//    }
//
//    private void checkMulti(List<String> inputs, String format, String... expected) {
//        final List<String> expectedList = new ArrayList<>();
//        Collections.addAll(expectedList, expected);
//        final List<String> parsed = ParameterParser.parse(inputs, format);
//        assertEquals(expectedList, parsed, "Unexpected results for format " + format);
//    }
}
