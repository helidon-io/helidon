/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import org.junit.jupiter.api.Test;

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
                + "HttpOnly";
        SetCookie setCookie = SetCookie.parse(template);
        assertThat(setCookie.toString(), is(template));
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
