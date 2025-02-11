/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.http.tests.cookie;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import io.helidon.http.DateTime;
import io.helidon.http.HeaderNames;
import io.helidon.http.SetCookie;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isIn;

/**
 * Validates that a cookie can be properly cleared in a handler method. An HTTP
 * cookie is identified by its name, domain and path. In this test, two cookies
 * with the same name but different domains are used.
 */
@ServerTest
class CookieTest {
    private static final String START_OF_YEAR_1970 =
            ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneId.of("GMT+0"))
                    .format(DateTime.RFC_1123_DATE_TIME);

    private final Http1Client client;

    CookieTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void route(HttpRouting.Builder router) {
        router.addFilter((chain, req, res) -> {
                    // adds two cookies same name different domains
                    res.headers().addCookie(newCookie("my-cookie", "my-domain1", "/"));
                    res.headers().addCookie(newCookie("my-cookie", "my-domain2", "/"));
                    chain.proceed();
                })
                .get("/cookie", (req, res) -> {
                    // clears cookie in my-domain2
                    res.headers().clearCookie(newCookie("my-cookie", "my-domain2", "/"));
                    res.status(200).send();
                });
    }

    @Test
    void clearCookieTest() {
        try (Http1ClientResponse res = client.get("/cookie").request()) {
            assertThat(res.headers().contains(HeaderNames.SET_COOKIE), is(true));
            List<String> values = res.headers().get(HeaderNames.SET_COOKIE).allValues();
            assertThat(values.size(), is(2));
            SetCookie first = SetCookie.parse(values.getFirst());
            validateCookie(first);
            SetCookie last = SetCookie.parse(values.getLast());
            validateCookie(last);
        }
    }

    static void validateCookie(SetCookie cookie) {
        assertThat(cookie.name(), is("my-cookie"));
        assertThat(cookie.path(), is(Optional.of("/")));
        assertThat(cookie.domain().isPresent(), is(true));
        assertThat(cookie.domain().get(), isIn(new String[] {"my-domain1", "my-domain2"}));
        if (cookie.domain().get().equals("my-domain1")) {
            assertThat(cookie.expires().isEmpty(), is(true));
        } else if (cookie.domain().get().equals("my-domain2")) {
            assertThat(cookie.expires().isPresent(), is(true));
            assertThat(cookie.expires().get().format(DateTime.RFC_1123_DATE_TIME), is(START_OF_YEAR_1970));      // cleared
        }
    }

    static SetCookie newCookie(String name, String domain, String path) {
        return SetCookie.builder(name, name + "-value")
                .domain(domain)
                .path(path)
                .build();
    }
}
