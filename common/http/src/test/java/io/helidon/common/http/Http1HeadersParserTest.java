/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import io.helidon.common.buffers.DataReader;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class Http1HeadersParserTest {
    private static String CUSTOM_HEADER_NAME = "Custom-Header-Name";
    private static String CUSTOM_HEADER_VALUE = "Custom-Header-Value";

    @Test
    void testHeadersAreCaseInsensitive() {
        DataReader reader = new DataReader(() -> (
                "Set-Cookie: c1=v1\r\nSet-Cookie: c2=v2\r\n"
                        + "Header: hv1\r\nheader: hv2\r\nheaDer: hv3\r\n"
                        + "\r\n").getBytes(StandardCharsets.US_ASCII));
        WritableHeaders<?> headers = Http1HeadersParser.readHeaders(reader, 1024, true);

        testHeader(headers, "Set-Cookie", "c1=v1", "c2=v2");
        testHeader(headers, "set-cookie", "c1=v1", "c2=v2");
        testHeader(headers, "SET-CooKIE", "c1=v1", "c2=v2");

        testHeader(headers, "header", "hv1", "hv2", "hv3");
        testHeader(headers, "Header", "hv1", "hv2", "hv3");
        testHeader(headers, "HeADer", "hv1", "hv2", "hv3");
    }

    @ParameterizedTest
    @MethodSource("headerValues")
    void testHeaderValuesWithValidationEnabled(String headerValue, boolean expectsValid) {
        // retrieve headers with validation enabled
        WritableHeaders<?> headers = getHeaders(CUSTOM_HEADER_NAME, headerValue, true);
        if (expectsValid) {
            String responseHeaderValue = headers.get(Http.Header.create(CUSTOM_HEADER_NAME)).values();
            // returned header values WhiteSpaces are trimmed so need to be tested with trimmed values
            assertThat(responseHeaderValue, is(headerValue.trim()));
        } else {
            Assertions.assertThrows(IllegalArgumentException.class,
                                    () -> headers.get(Http.Header.create(CUSTOM_HEADER_NAME)).values());
        }
    }

    @ParameterizedTest
    @MethodSource("headerValues")
    void testHeaderValuesWithValidationDisabled(String headerValue) {
        // retrieve headers without validating
        WritableHeaders<?> headers = getHeaders(CUSTOM_HEADER_NAME, headerValue, false);
        String responseHeaderValue = headers.get(Http.Header.create(CUSTOM_HEADER_NAME)).values();
        // returned header values WhiteSpaces are trimmed so need to be tested with trimmed values
        assertThat(responseHeaderValue, is(headerValue.trim()));
    }

    @ParameterizedTest
    @MethodSource("headerNames")
    void testHeaderNamesWithValidationEnabled(String headerName, boolean expectsValid) {
        boolean validate = true;
        if (expectsValid) {
            WritableHeaders<?> headers = getHeaders(headerName, CUSTOM_HEADER_VALUE, validate);
            String responseHeaderValue = headers.get(Http.Header.create(headerName)).values();
            assertThat(responseHeaderValue, is(CUSTOM_HEADER_VALUE));
        } else {
            Assertions.assertThrows(IllegalArgumentException.class,
                                    () -> getHeaders(headerName, CUSTOM_HEADER_VALUE, validate));
        }
    }

    @ParameterizedTest
    @MethodSource("headerValues")
    void testHeaderNamesWithValidationDisabled(String headerName) {
        // retrieve headers without validating
        WritableHeaders<?> headers = getHeaders(headerName, CUSTOM_HEADER_VALUE, false);
        String responseHeaderValue = headers.get(Http.Header.create(headerName)).values();
        // returned header values WhiteSpaces are trimmed so need to be tested with trimmed values
        assertThat(responseHeaderValue, is(CUSTOM_HEADER_VALUE));
    }

    private static WritableHeaders<?> getHeaders(String headerName, String headerValue, boolean validate) {
        DataReader reader =
                new DataReader(() -> (headerName + ":" + headerValue + "\r\n" + "\r\n").getBytes(StandardCharsets.US_ASCII));
        return Http1HeadersParser.readHeaders(reader, 1024, validate);
    }

    private static Stream<Arguments> headerValues() {
        return Stream.of(
                // Valid header values
                arguments("Header Value", true),
                arguments("HeaderValue1\u0009, Header=Value2", true),
                arguments("Header\tValue", true),
                arguments(" Header Value ", true),
                // Invalid header values
                arguments("H\u001ceaderValue1", false),
                arguments("HeaderValue1, Header\u007fValue", false),
                arguments("HeaderValue1\u001f, HeaderValue2", false)
        );
    }

    private static Stream<Arguments> headerNames() {
        return Stream.of(
                // Invalid header names
                arguments("Header\u001aName", false),
                arguments("Header\u000EName", false),
                arguments("HeaderName\r\n", false),
                arguments("(Header:Name)", false),
                arguments("<Header?Name>", false),
                arguments("{Header=Name}", false),
                arguments("\"HeaderName\"", false),
                arguments("[\\HeaderName]", false),
                arguments("@Header,Name;", false),
                // Valid header names
                arguments("!#$Custom~%&\'*Header+^`|", true),
                arguments("Custom_0-9_a-z_A-Z_Header", true)
        );
    }

    private void testHeader(Headers headers, String header, String... values) {
        Http.HeaderName headerName = Http.Header.create(header);
        assertThat("Headers should contain header: " + headerName.lowerCase(),
                   headers.contains(headerName),
                   is(true));
        assertThat("Header " + headerName.lowerCase(),
                   headers.get(headerName).allValues(),
                   hasItems(values));
    }
}
