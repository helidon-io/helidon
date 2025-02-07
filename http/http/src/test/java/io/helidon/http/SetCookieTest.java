/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for verification of {@link SetCookie} proper parsing from String.
 */
public class SetCookieTest {

    private static final String TEMPLATE = "some-cookie=some-cookie-value; "
            + "Expires=Thu, 22 Oct 2015 07:28:00 GMT; "
            + "Max-Age=2592000; "
            + "Domain=domain.value; "
            + "Path=/; "
            + "Secure; "
            + "HttpOnly; "
            + "SameSite=Lax";

    @Test
    public void testSetCookiesFromString() {
        SetCookie setCookie = SetCookie.parse(TEMPLATE);

        assertThat(setCookie.name(), is("some-cookie"));
        assertThat(setCookie.value(), is("some-cookie-value"));
        assertThat(setCookie.expires(), optionalValue(is(DateTime.parse("Thu, 22 Oct 2015 07:28:00 GMT"))));
        assertThat(setCookie.maxAge(), optionalValue(is(Duration.ofSeconds(2592000))));
        assertThat(setCookie.domain(), optionalValue(is("domain.value")));
        assertThat(setCookie.path(), optionalValue(is("/")));
        assertThat(setCookie.secure(), is(true));
        assertThat(setCookie.httpOnly(), is(true));
        assertThat(setCookie.sameSite(), optionalValue(is(SetCookie.SameSite.LAX)));

        assertThat("Generate same cookie value", setCookie.toString(), is(TEMPLATE));
    }

    @Test
    public void testSetCookiesInvalidValue() {
        String template = "some-cookie=some-cookie-value; "
                + "Invalid=value";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                   () -> SetCookie.parse(template));
        assertThat(ex.getMessage(), is("Unexpected Set-Cookie part: Invalid"));
    }

    @Test
    public void testEmptyValue() {
        SetCookie setCookie = SetCookie.parse("some-cookie=");
        assertThat(setCookie.value(), is(""));
    }

    @Test
    public void testEquals() {
        SetCookie setCookie1 = SetCookie.parse(TEMPLATE);
        SetCookie setCookie2 = SetCookie.builder("some-cookie", "").build();
        SetCookie setCookie3 = SetCookie.builder("some-cookie", "")
                .path("/")
                .domain("domain.value")
                .expires(Instant.now())     // ignored in equals
                .build();

        assertThat("They match name, path and domain",
                   setCookie1.equals(setCookie3), is(true));
        assertThat("They match name, path and domain",
                   setCookie1.hashCode(), is(setCookie3.hashCode()));
        assertThat("They do not match path or domain",
                   setCookie1.equals(setCookie2), is(false));
    }

    @Test
    public void testCookieBuilder() {
        SetCookie setCookie1 = SetCookie.parse(TEMPLATE);
        SetCookie setCookie2 = SetCookie.builder(setCookie1).build();       // from setCookie1

        assertThat(setCookie1.equals(setCookie2), is(true));
        assertThat(setCookie1.hashCode(), is(setCookie2.hashCode()));
        assertThat(setCookie2.name(), is("some-cookie"));
        assertThat(setCookie2.value(), is("some-cookie-value"));
        assertThat(setCookie2.expires(), optionalValue(is(DateTime.parse("Thu, 22 Oct 2015 07:28:00 GMT"))));
        assertThat(setCookie2.maxAge(), optionalValue(is(Duration.ofSeconds(2592000))));
        assertThat(setCookie2.domain(), optionalValue(is("domain.value")));
        assertThat(setCookie2.path(), optionalValue(is("/")));
        assertThat(setCookie2.secure(), is(true));
        assertThat(setCookie2.httpOnly(), is(true));
        assertThat(setCookie2.sameSite(), optionalValue(is(SetCookie.SameSite.LAX)));
    }
}
