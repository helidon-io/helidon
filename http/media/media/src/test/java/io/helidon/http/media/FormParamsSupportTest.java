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

package io.helidon.http.media;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import io.helidon.common.parameters.Parameters;
import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormParamsSupportTest {
    private static final MediaSupport SUPPORT = FormParamsSupport.create();
    private static final String URL_ENCODED = "application/x-www-form-urlencoded;charset=UTF-8";
    private static final String PLAIN_TEXT = "text/plain;charset=UTF-8";

    @TestFactory
    Stream<DynamicTest> readsUrlEncodedFields() {
        Map<String, Map<String, List<String>>> cases = Map.ofEntries(
                Map.entry("", Map.of()),
                Map.entry("&&", Map.of()),
                Map.entry("q", Map.of("q", List.of(""))),
                Map.entry("q=", Map.of("q", List.of(""))),
                Map.entry("=value", Map.of("", List.of("value"))),
                Map.entry("=", Map.of("", List.of(""))),
                Map.entry("=a=b", Map.of("", List.of("a=b"))),
                Map.entry("q=a=b", Map.of("q", List.of("a=b"))),
                Map.entry("q&x=1", Map.of("q", List.of(""), "x", List.of("1"))),
                Map.entry("a=1&b", Map.of("a", List.of("1"), "b", List.of(""))),
                Map.entry("&q&&q=&q=one+two&&q&", Map.of("q", List.of("", "", "one two", ""))),
                Map.entry("=first&&=&=last", Map.of("", List.of("first", "", "last"))),
                Map.entry("a%26b=c%3Dd%26e&%3D=%26&%2B=+%2B&caf%C3%A9=%E2%82%AC",
                          Map.of("a&b", List.of("c=d&e"), "=", List.of("&"), "+", List.of(" +"), "café", List.of("€"))),
                Map.entry("multiple=value1,value2&special=some+%26%40%23%2F+special+value&noValue=",
                          Map.of("multiple", List.of("value1,value2"),
                                 "special", List.of("some &@#/ special value"), "noValue", List.of(""))));

        return cases.entrySet().stream().flatMap(entry -> Stream.of(
                DynamicTest.dynamicTest("request: " + entry.getKey(),
                                        () -> assertThat(read(entry.getKey(), URL_ENCODED).toMap(), is(entry.getValue()))),
                DynamicTest.dynamicTest("response: " + entry.getKey(),
                                        () -> assertThat(readResponse(entry.getKey()).toMap(), is(entry.getValue())))));
    }

    @Test
    void readsEmptyUrlEncodedValue() {
        assertValues(read("q=", URL_ENCODED), List.of(""));
    }

    @Test
    void readsEmptyPlainTextValue() {
        assertValues(read("q=", PLAIN_TEXT), List.of(""));
    }

    @Test
    void preservesEmptyUrlEncodedValuesInOrder() {
        assertValues(read("q=&q=one+two&q=&q=three%2Cfour&q=", URL_ENCODED),
                     List.of("", "one two", "", "three,four", ""));
    }

    @Test
    void preservesEmptyPlainTextValuesInOrder() {
        assertValues(read("q=\nq=one two\nq=\nq=three,four\nq=", PLAIN_TEXT),
                     List.of("", "one two", "", "three,four", ""));
    }

    @Test
    void preservesRepeatedEmptyValues() {
        assertValues(read("q=&q=", URL_ENCODED), List.of("", ""));
        assertValues(read("q=\nq=", PLAIN_TEXT), List.of("", ""));
    }

    @Test
    void readsEmptyResponseValue() {
        assertValues(readResponse("q="), List.of(""));
    }

    @Test
    void distinguishesMissingParameterFromEmptyValue() {
        for (String contentType : List.of(URL_ENCODED, PLAIN_TEXT)) {
            Parameters parameters = read("other=", contentType);

            assertThat(contentType, parameters.contains("q"), is(false));
            assertThrows(NoSuchElementException.class, () -> parameters.get("q"));
            assertThrows(NoSuchElementException.class, () -> parameters.all("q"));
            assertThat(contentType, read("", contentType).isEmpty(), is(true));
        }
    }

    private static Parameters readResponse(String body) {
        var requestHeaders = WritableHeaders.create();
        var responseHeaders = WritableHeaders.create().set(HeaderNames.CONTENT_TYPE, URL_ENCODED);
        var stream = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));

        return SUPPORT.reader(Parameters.GENERIC_TYPE, requestHeaders, responseHeaders)
                .supplier().get().read(Parameters.GENERIC_TYPE, stream, requestHeaders, responseHeaders);
    }

    private static Parameters read(String body, String contentType) {
        var headers = WritableHeaders.create().set(HeaderNames.CONTENT_TYPE, contentType);
        var stream = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        return SUPPORT.reader(Parameters.GENERIC_TYPE, headers)
                .supplier().get().read(Parameters.GENERIC_TYPE, stream, headers);
    }

    private static void assertValues(Parameters parameters, List<String> expected) {
        assertThat(parameters.contains("q"), is(true));
        assertThat(parameters.get("q"), is(expected.getFirst()));
        assertThat(parameters.all("q"), is(expected));
    }
}
