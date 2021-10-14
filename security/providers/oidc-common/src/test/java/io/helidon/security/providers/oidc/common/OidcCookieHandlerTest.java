/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.security.providers.oidc.common;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.reactive.Single;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class OidcCookieHandlerTest {
    private static OidcCookieHandler handler;

    @BeforeAll
    static void initClass() {
        handler = OidcCookieHandler.builder()
                .encryptionEnabled(false)
                .cookieName("COOKIE")
                .build();
    }

    @Test
    void testFindCookieMissing() {
        Map<String, List<String>> headers = Map.of();
        Optional<Single<String>> cookie = handler.findCookie(headers);

        assertThat(cookie, is(Optional.empty()));
    }

    @Test
    void testFindCookiePresent() {
        String expectedValue = "cookieValue";
        Map<String, List<String>> headers = Map.of("Accept", List.of("application/json"),
                                                   "Cookie", List.of("COOKIE=" + expectedValue));
        Optional<Single<String>> cookie = handler.findCookie(headers);

        assertThat(cookie, not(Optional.empty()));
        String cookieValue = cookie.get().await();
        assertThat(cookieValue, is(expectedValue));

        headers = Map.of("Accept", List.of("application/json"),
                         "Cookie", List.of("COOKIE=" + expectedValue + ";abc=bbc;uao=aee"));
        cookie = handler.findCookie(headers);

        assertThat(cookie, not(Optional.empty()));
        cookieValue = cookie.get().await();
        assertThat(cookieValue, is(expectedValue));

        headers = Map.of("Accept", List.of("application/json"),
                         "Cookie", List.of("abc=bbc; COOKIE=" + expectedValue + ";uao=aee"));
        cookie = handler.findCookie(headers);

        assertThat(cookie, not(Optional.empty()));
        cookieValue = cookie.get().await();
        assertThat(cookieValue, is(expectedValue));

        headers = Map.of("Accept", List.of("application/json"),
                         "Cookie", List.of("abc=bbc;uao=aee;COOKIE=" + expectedValue));
        cookie = handler.findCookie(headers);

        assertThat(cookie, not(Optional.empty()));
        cookieValue = cookie.get().await();
        assertThat(cookieValue, is(expectedValue));
    }
}
