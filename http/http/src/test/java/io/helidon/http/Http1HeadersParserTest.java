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

package io.helidon.http;

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
    public static final String VALID_HEADER_VALUE = "Valid-Header-Value";
    public static final String VALID_HEADER_NAME = "Valid-Header-Name";

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
    @MethodSource("headers")
    void testHeadersWithValidationEnabled(String headerName, String headerValue, boolean expectsValid) {
        // retrieve headers with validation enabled
        WritableHeaders<?> headers;
        if (expectsValid) {
            headers = getHeaders(headerName, headerValue, true);
            String responseHeaderValue = headers.get(HeaderNames.create(headerName)).values();
            // returned header values WhiteSpaces are trimmed so need to be tested with trimmed values
            assertThat(responseHeaderValue, is(headerValue.trim()));
        } else {
            Assertions.assertThrows(IllegalArgumentException.class,
                                    () -> getHeaders(headerName, headerValue, true));
        }
    }

    @ParameterizedTest
    @MethodSource("headers")
    void testHeadersWithValidationDisabled(String headerValue) {
        // retrieve headers without validating
        WritableHeaders<?> headers = getHeaders(CUSTOM_HEADER_NAME, headerValue, false);
        String responseHeaderValue = headers.get(HeaderNames.create(CUSTOM_HEADER_NAME)).values();
        // returned header values WhiteSpaces are trimmed so need to be tested with trimmed values
        assertThat(responseHeaderValue, is(headerValue.trim()));
    }

    private static WritableHeaders<?> getHeaders(String headerName, String headerValue, boolean validate) {
        DataReader reader =
                new DataReader(() -> (headerName + ":" + headerValue + "\r\n" + "\r\n").getBytes(StandardCharsets.US_ASCII));
        return Http1HeadersParser.readHeaders(reader, 1024, validate);
    }

    private static Stream<Arguments> headers() {
        return Stream.of(
                // Invalid header names
                arguments("Header\u001aName", VALID_HEADER_VALUE, false),
                arguments("Header\u000EName", VALID_HEADER_VALUE, false),
                arguments("HeaderName\r\n", VALID_HEADER_VALUE, false),
                arguments("(Header:Name)", VALID_HEADER_VALUE, false),
                arguments("<Header?Name>", VALID_HEADER_VALUE, false),
                arguments("{Header=Name}", VALID_HEADER_VALUE, false),
                arguments("\"HeaderName\"", VALID_HEADER_VALUE, false),
                arguments("[\\HeaderName]", VALID_HEADER_VALUE, false),
                arguments("@Header,Name;", VALID_HEADER_VALUE, false),
                // Valid header names
                arguments("!#$Custom~%&\'*Header+^`|", VALID_HEADER_VALUE, true),
                arguments("Custom_0-9_a-z_A-Z_Header", VALID_HEADER_VALUE, true),
                // Valid header values
                arguments(VALID_HEADER_NAME, "Header Value", true),
                arguments(VALID_HEADER_NAME, "HeaderValue1\u0009, Header=Value2", true),
                arguments(VALID_HEADER_NAME, "Header\tValue", true),
                arguments(VALID_HEADER_NAME, " Header Value ", true),
                // Invalid header values
                arguments(VALID_HEADER_NAME, "H\u001ceaderValue1", false),
                arguments(VALID_HEADER_NAME, "HeaderValue1, Header\u007fValue", false),
                arguments(VALID_HEADER_NAME, "HeaderValue1\u001f, HeaderValue2", false)
        );
    }


    private void testHeader(Headers headers, String header, String... values) {
        HeaderName headerName = HeaderNames.create(header);
        assertThat("Headers should contain header: " + headerName.lowerCase(),
                   headers.contains(headerName),
                   is(true));
        assertThat("Header " + headerName.lowerCase(),
                   headers.get(headerName).allValues(),
                   hasItems(values));
    }
}
