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
 */

package io.helidon.common.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SetCookieTest {

    @Test
    public void testExpireswithInstant() {
        SetCookie setCookie = new SetCookie("foo", "bar");
        String date =
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(1558783353000l),
                        ZoneId.of("GMT"))
                        .format(DateTimeFormatter.RFC_1123_DATE_TIME);

        assertEquals("foo=bar", setCookie.expires((Instant) null).toString());
        assertEquals("foo=bar; Expires=" + date,
                setCookie.expires(Instant.ofEpochMilli(1558783353000l))
                        .toString());
    }

    @Test
    public void testDomainAndPath() throws URISyntaxException {
        SetCookie setCookie = new SetCookie("foo", "bar");
        assertEquals("foo=bar", setCookie.domainAndPath(null).toString());
        assertEquals("foo=bar; Path=baz",
                setCookie.domainAndPath(new URI("baz")).toString());
    }

    @Test
    public void testToString1() {
        SetCookie setCookie = new SetCookie("foo", "bar");
        setCookie.maxAge(Duration.ofMinutes(1000));
        setCookie.domain("http://");
        setCookie.path("baz");
        setCookie.secure(true);
        setCookie.httpOnly(true);
        setCookie.expires(
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(1558783353000l),
                        ZoneOffset.UTC));

        String date =
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(1558783353000l),
                        ZoneId.of("GMT"))
                        .format(DateTimeFormatter.RFC_1123_DATE_TIME);

        String retval =
                "; Max-Age=60000; Domain=http://; Path=baz; Secure; HttpOnly";
        assertEquals("foo=bar; Expires=" + date + retval,
                setCookie.toString());
    }

    @Test
    public void testToString2() {
        SetCookie setCookie = new SetCookie("foo", "bar");
        setCookie.maxAge(null);
        setCookie.domain(null);
        setCookie.path(null);
        setCookie.secure(false);
        setCookie.httpOnly(false);
        setCookie.expires(
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(1558783353000l),
                        ZoneOffset.UTC));

        String date =
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(1558783353000l),
                        ZoneId.of("GMT"))
                        .format(DateTimeFormatter.RFC_1123_DATE_TIME);

        assertEquals("foo=bar; Expires=" + date, setCookie.toString());
    }
}
