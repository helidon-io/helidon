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

package io.helidon.webclient.tests;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class CookieRedirectLeakTest {
    private static final String TRUSTED_HOST = "127.0.0.1";
    private static final String COLLECTOR_HOST = "localhost";
    private static final String DEFAULT_COOKIE = "session=redteam-token";
    private static final String STORED_COOKIE = "session=redteam-token";
    private static final String SET_STORED_COOKIE = STORED_COOKIE + "; Path=/";
    private static final String TARGET_COOKIE = "target=attacker-token";
    private static final String SET_TARGET_COOKIE = TARGET_COOKIE + "; Path=/";

    private static final AtomicReference<String> TRUSTED_COOKIE = new AtomicReference<>();
    private static final AtomicReference<String> LEAKED_STEP_COOKIE = new AtomicReference<>();
    private static final AtomicReference<String> LEAKED_COLLECT_COOKIE = new AtomicReference<>();

    private static WebServer redirector;
    private static WebServer collector;
    private static Http1Client storedCookieClient;
    private static Http1Client defaultCookieClient;

    @BeforeAll
    static void beforeAll() {
        collector = WebServer.builder()
                .host(COLLECTOR_HOST)
                .routing(rules -> rules.get("/prime", CookieRedirectLeakTest::primeTargetHandler)
                        .get("/step", CookieRedirectLeakTest::stepHandler)
                        .get("/collect", CookieRedirectLeakTest::collectHandler))
                .build()
                .start();

        redirector = WebServer.builder()
                .host(TRUSTED_HOST)
                .routing(rules -> rules.get("/prime", CookieRedirectLeakTest::primeTrustedHandler)
                        .get("/bounce", CookieRedirectLeakTest::redirectHandler))
                .build()
                .start();

        Config storedConfig = Config.builder()
                .addSource(ConfigSources.classpath("cookie-redirect-leak.yaml"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        storedCookieClient = Http1Client.builder()
                .config(storedConfig.get("client"))
                .baseUri("http://" + TRUSTED_HOST + ":" + redirector.port())
                .build();

        Config defaultConfig = Config.builder()
                .addSource(ConfigSources.classpath("cookie-redirect-leak-default.yaml"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        defaultCookieClient = Http1Client.builder()
                .config(defaultConfig.get("client"))
                .baseUri("http://" + TRUSTED_HOST + ":" + redirector.port())
                .build();
    }

    @AfterAll
    static void afterAll() {
        if (redirector != null) {
            redirector.stop();
        }
        if (collector != null) {
            collector.stop();
        }
    }

    @Test
    void storedCookiesShouldFollowRedirectTargetScope() {
        TRUSTED_COOKIE.set(null);
        LEAKED_STEP_COOKIE.set(null);
        LEAKED_COLLECT_COOKIE.set(null);

        try (Http1ClientResponse response = storedCookieClient.get("http://" + COLLECTOR_HOST + ":" + collector.port() + "/prime")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        try (Http1ClientResponse response = storedCookieClient.get("/prime").request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        try (Http1ClientResponse response = storedCookieClient.get("/bounce").request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(TRUSTED_COOKIE.get(), is(STORED_COOKIE));
        assertThat("Redirect target cookie was not sent to attacker step: " + LEAKED_STEP_COOKIE.get(),
                   LEAKED_STEP_COOKIE.get(), is(TARGET_COOKIE));
        assertThat("Redirect target cookie was not sent to attacker collector: " + LEAKED_COLLECT_COOKIE.get(),
                   LEAKED_COLLECT_COOKIE.get(), is(TARGET_COOKIE));
    }

    @Test
    void defaultCookiesShouldNotCrossHostRedirectBoundary() {
        TRUSTED_COOKIE.set(null);
        LEAKED_STEP_COOKIE.set(null);
        LEAKED_COLLECT_COOKIE.set(null);

        try (Http1ClientResponse response = defaultCookieClient.get("/bounce").request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(TRUSTED_COOKIE.get(), is(DEFAULT_COOKIE));
        assertThat("Cross-host redirect leaked default Cookie header to attacker step: " + LEAKED_STEP_COOKIE.get(),
                   LEAKED_STEP_COOKIE.get(), is(nullValue()));
        assertThat("Cross-host redirect leaked default Cookie header to attacker collector: "
                           + LEAKED_COLLECT_COOKIE.get(),
                   LEAKED_COLLECT_COOKIE.get(), is(nullValue()));
    }

    private static void primeTrustedHandler(ServerRequest req, ServerResponse res) {
        res.header(HeaderNames.SET_COOKIE, SET_STORED_COOKIE)
                .status(Status.OK_200)
                .send();
    }

    private static void primeTargetHandler(ServerRequest req, ServerResponse res) {
        res.header(HeaderNames.SET_COOKIE, SET_TARGET_COOKIE)
                .status(Status.OK_200)
                .send();
    }

    private static void redirectHandler(ServerRequest req, ServerResponse res) {
        TRUSTED_COOKIE.set(extractCookie(req));
        res.status(Status.FOUND_302)
                .header(HeaderNames.LOCATION, "http://" + COLLECTOR_HOST + ":" + collector.port() + "/step")
                .send();
    }

    private static void stepHandler(ServerRequest req, ServerResponse res) {
        LEAKED_STEP_COOKIE.set(extractCookie(req));
        res.status(Status.FOUND_302)
                .header(HeaderNames.LOCATION, "http://" + COLLECTOR_HOST + ":" + collector.port() + "/collect")
                .send();
    }

    private static void collectHandler(ServerRequest req, ServerResponse res) {
        LEAKED_COLLECT_COOKIE.set(extractCookie(req));
        res.status(Status.OK_200).send("collector reached");
    }

    private static String extractCookie(ServerRequest req) {
        if (!req.headers().contains(HeaderNames.COOKIE)) {
            return null;
        }

        List<String> cookieValues = req.headers().get(HeaderNames.COOKIE).allValues();
        return String.join("; ", cookieValues);
    }
}
