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

package io.helidon.webserver.security;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Security;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class ConfiguredMethodSecurityTest {
    private static final AtomicInteger DUPLICATE_AUTHENTICATIONS = new AtomicInteger();

    private final Http1Client client;

    ConfiguredMethodSecurityTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder serverBuilder) {
        var config = Config.just("""
                paths:
                  - path: /lowercase
                    methods: [get]
                    authenticate: true
                  - path: /mixed
                    methods: [PoSt]
                    authenticate: true
                  - path: /custom
                    methods: [Follow]
                    authenticate: true
                  - path: /uppercase
                    methods: [GET]
                    authenticate: true
                  - path: /absent
                    authenticate: true
                  - path: /empty
                    methods: []
                    authenticate: true
                  - path: /duplicates
                    methods: [get, GET, get]
                    authentication-optional: true
                """, MediaTypes.APPLICATION_YAML);
        var security = Security.builder()
                .addAuthenticationProvider(request -> {
                    if (request.env().path().orElseThrow().equals("/duplicates")) {
                        DUPLICATE_AUTHENTICATIONS.incrementAndGet();
                    }
                    return AuthenticationResponse.abstain();
                })
                .build();

        serverBuilder.featuresDiscoverServices(false)
                .addFeature(ContextFeature.create())
                .addFeature(SecurityFeature.builder()
                                    .security(security)
                                    .config(config)
                                    .addPath(path -> path.path("/programmatic")
                                            .addMethod(Method.create("get"))
                                            .handler(SecurityFeature.authenticate()))
                                    .build())
                .routing(routing -> routing.any("/*", (_, response) -> response.send("application response")));
    }

    @Test
    void lowercaseConfigurationProtectsExactAndStandardMethod() {
        assertStatus("/lowercase", "get", Status.UNAUTHORIZED_401);
        assertStatus("/lowercase", "GET", Status.UNAUTHORIZED_401);
        assertStatus("/lowercase", "Get", Status.OK_200);
    }

    @Test
    void mixedCaseConfigurationProtectsExactAndStandardMethod() {
        assertStatus("/mixed", "PoSt", Status.UNAUTHORIZED_401);
        assertStatus("/mixed", "POST", Status.UNAUTHORIZED_401);
        assertStatus("/mixed", "post", Status.OK_200);
    }

    @Test
    void customMethodRemainsCaseSensitive() {
        assertStatus("/custom", "Follow", Status.UNAUTHORIZED_401);
        assertStatus("/custom", "FOLLOW", Status.OK_200);
        assertStatus("/custom", "follow", Status.OK_200);
    }

    @Test
    void uppercaseConfigurationRemainsCaseSensitive() {
        assertStatus("/uppercase", "GET", Status.UNAUTHORIZED_401);
        assertStatus("/uppercase", "get", Status.OK_200);
    }

    @Test
    void absentAndEmptyMethodsProtectAllMethods() {
        for (var path : List.of("/absent", "/empty")) {
            for (var method : List.of("GET", "get", "PoSt", "Follow")) {
                assertStatus(path, method, Status.UNAUTHORIZED_401);
            }
        }
    }

    @Test
    void programmaticMethodRemainsCaseSensitive() {
        assertStatus("/programmatic", "get", Status.UNAUTHORIZED_401);
        assertStatus("/programmatic", "GET", Status.OK_200);
    }

    @Test
    void duplicateAliasesInvokeAuthenticationOnce() {
        for (var method : List.of("get", "GET")) {
            int before = DUPLICATE_AUTHENTICATIONS.get();

            assertStatus("/duplicates", method, Status.OK_200);

            assertThat(method + " authentication count", DUPLICATE_AUTHENTICATIONS.get() - before, is(1));
        }
    }

    private void assertStatus(String path, String method, Status expectedStatus) {
        try (var response = client.method(Method.create(method)).path(path).request()) {
            assertThat(method + " " + path, response.status(), is(expectedStatus));
            if (expectedStatus.equals(Status.OK_200)) {
                assertThat(method + " " + path, response.as(String.class), is("application response"));
            }
        }
    }
}
