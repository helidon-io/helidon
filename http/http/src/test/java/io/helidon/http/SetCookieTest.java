/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for verification of {@link SetCookie} proper parsing from String.
 */
public class SetCookieTest {

    @Test
    public void testSetCookiesFromString() {
        String template = "some-cookie=some-cookie-value; "
                + "Expires=Thu, 22 Oct 2015 07:28:00 GMT; "
                + "Max-Age=2592000; "
                + "Domain=domain.value; "
                + "Path=/; "
                + "Secure; "
                + "HttpOnly; "
                + "SameSite=Lax";
        SetCookie setCookie = SetCookie.parse(template);

        assertThat(setCookie.name(), is("some-cookie"));
        assertThat(setCookie.value(), is("some-cookie-value"));
        assertThat(setCookie.expires(), optionalValue(is(DateTime.parse("Thu, 22 Oct 2015 07:28:00 GMT"))));
        assertThat(setCookie.maxAge(), optionalValue(is(Duration.ofSeconds(2592000))));
        assertThat(setCookie.domain(), optionalValue(is("domain.value")));
        assertThat(setCookie.path(), optionalValue(is("/")));
        assertThat(setCookie.secure(), is(true));
        assertThat(setCookie.httpOnly(), is(true));
        assertThat(setCookie.sameSite(), optionalValue(is(SetCookie.SameSite.LAX)));

        assertThat("Generate same cookie value", setCookie.toString(), is(template));
    }

    @Test
    public void testSetCookiesInvalidValue() {
        String template = "some-cookie=some-cookie-value; "
                + "Invalid=value";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                   () -> SetCookie.parse(template));
        assertThat(ex.getMessage(), is("Unexpected Set-Cookie part: Invalid"));
    }

}
