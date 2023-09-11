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

import java.util.stream.Stream;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Unit test for {@link Http}.
 */
class HttpTest {
    @Test
    void testStatusIsStatus() {
        Status rs = Status.create(Status.TEMPORARY_REDIRECT_307.code());
        assertThat(rs, sameInstance(Status.TEMPORARY_REDIRECT_307));
    }

    @Test
    void testStatusWithReasonIsStatus() {
        Status rs = Status
                .create(Status.TEMPORARY_REDIRECT_307.code(), Status.TEMPORARY_REDIRECT_307.reasonPhrase().toUpperCase());
        assertThat(rs, sameInstance(Status.TEMPORARY_REDIRECT_307));
    }

    @Test
    void testResposneStatusCustomReason() {
        Status rs = Status
                .create(Status.TEMPORARY_REDIRECT_307.code(), "Custom reason phrase");
        assertThat(rs, CoreMatchers.not(Status.TEMPORARY_REDIRECT_307));
        assertThat(rs.reasonPhrase(), is("Custom reason phrase"));
        MatcherAssert.assertThat(rs.code(), CoreMatchers.is(Status.TEMPORARY_REDIRECT_307.code()));
        MatcherAssert.assertThat(rs.family(), CoreMatchers.is(Status.TEMPORARY_REDIRECT_307.family()));
    }

    @ParameterizedTest
    @MethodSource("headers")
    void testHeaderValidation(String headerName, String headerValues, boolean expectsValid) {
        Header header = HeaderValues.create(headerName, headerValues);
        if (expectsValid) {
            header.validate();
        } else {
            Assertions.assertThrows(IllegalArgumentException.class, () -> header.validate());
        }
    }

    private static Stream<Arguments> headers() {
        return Stream.of(
                // Valid headers
                arguments("!#$Custom~%&\'*Header+^`|", "!Header\tValue~", true),
                arguments("Custom_0-9_a-z_A-Z_Header", "\u0080Header Value\u00ff", true),
                // Invalid headers
                arguments("Valid-Header-Name", "H\u001ceaderValue1", false),
                arguments("Valid-Header-Name", "HeaderValue1, Header\u007fValue", false),
                arguments("Valid-Header-Name", "HeaderValue1\u001f, HeaderValue2", false),
                arguments("Header\u001aName", "Valid-Header-Value", false),
                arguments("Header\u000EName", "Valid-Header-Value", false),
                arguments("HeaderName\r\n", "Valid-Header-Value", false),
                arguments("(Header:Name)", "Valid-Header-Value", false),
                arguments("<Header?Name>", "Valid-Header-Value", false),
                arguments("{Header=Name}", "Valid-Header-Value", false),
                arguments("\"HeaderName\"", "Valid-Header-Value", false),
                arguments("[\\HeaderName]", "Valid-Header-Value", false),
                arguments("@Header,Name;", "Valid-Header-Value", false)
        );
    }
}
