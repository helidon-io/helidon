/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

package io.helidon.security.providers.httpauth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Principal;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityResponse;
import io.helidon.security.Subject;
import io.helidon.security.spi.AuthenticationProvider;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link HttpBasicAuthProvider}.
 */
public class HttpAuthProviderBuilderTest {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static Security security;

    private SecurityContext context;

    @BeforeAll
    public static void initClass() {
        SecureUserStore us = userStore();

        security = Security.builder()
                .addAuthenticationProvider(basicAuthProvider(false, us), "basic")
                .addAuthenticationProvider(basicAuthProvider(true, us), "basic_optional")
                .build();
    }

    private static SecureUserStore userStore() {
        return login -> {
            if ("jack".equals(login)) {
                return Optional.of(new TestUser("jack",
                                                "jackIsGreat".toCharArray(),
                                                Set.of("user", "admin")));
            }
            if ("jill".equals(login)) {
                return Optional.of(new TestUser("jill",
                                                "password".toCharArray(),
                                                Set.of("user")));
            }

            return Optional.empty();
        };
    }

    private static Supplier<? extends AuthenticationProvider> basicAuthProvider(boolean optional, SecureUserStore us) {
        return HttpBasicAuthProvider.builder()
                .realm("mic")
                .optional(optional)
                .userStore(us);
    }

    @BeforeEach
    public void init() {
        context = security.contextBuilder(String.valueOf(COUNTER.getAndIncrement()))
                .build();
    }

    @Test
    public void basicTestOptional() {
        AuthenticationResponse response = context.atnClientBuilder().explicitProvider("basic_optional").buildAndGet();

        assertThat(response.status().isSuccess(), is(false));
        assertThat(response.status().name(), is(SecurityResponse.SecurityStatus.ABSTAIN.name()));
        assertThat(response.statusCode().orElse(200), is(200));
        assertThat(response.description().orElse(""), is("No authorization header"));

        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, buildBasic("jack", "invalid_passworrd"));
        response = context.atnClientBuilder().explicitProvider("basic_optional").buildAndGet();

        assertThat(response.status().isSuccess(), is(false));
        assertThat(response.status().name(), is(SecurityResponse.SecurityStatus.ABSTAIN.name()));
        assertThat(response.statusCode().orElse(200), is(200));
        assertThat(response.description().orElse(""), is("Invalid username or password"));
    }

    @Test
    public void basicTestFail() {
        AuthenticationResponse response = context.atnClientBuilder().buildAndGet();

        assertThat(response.status().isSuccess(), is(false));
        assertThat(response.statusCode().orElse(200), is(401));
        String authHeader = response.responseHeaders().get(HttpBasicAuthProvider.HEADER_AUTHENTICATION_REQUIRED).get(0);
        assertThat(authHeader, notNullValue());
        assertThat(authHeader.toLowerCase(), is("basic realm=\"mic\""));
    }

    @Test
    public void basicTestJack() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, buildBasic("jack", "jackIsGreat"));

        AuthenticationResponse response = context.authenticate();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.statusCode().orElse(200), is(200));

        assertThat(getUsername(context), is("jack"));
        assertThat(context.isUserInRole("admin"), is(true));
        assertThat(context.isUserInRole("user"), is(true));
    }

    @Test
    public void basicTestInvalidUser() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, buildBasic("wrong", "user"));

        AuthenticationResponse response = context.authenticate();

        assertThat(response.description().orElse(""), is("Invalid username or password"));
        assertThat(response.status().isSuccess(), is(false));
        assertThat(response.statusCode().orElse(200), is(401));
        String authHeader = response.responseHeaders().get(HttpBasicAuthProvider.HEADER_AUTHENTICATION_REQUIRED).get(0);
        assertThat(authHeader, notNullValue());
        assertThat(authHeader.toLowerCase(), is("basic realm=\"mic\""));

        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, buildBasic("jack", "invalid_passworrd"));

        response = context.authenticate();

        assertThat(response.description().orElse(""), is("Invalid username or password"));
        assertThat(response.status().isSuccess(), is(false));
        assertThat(response.statusCode().orElse(200), is(401));
    }

    @Test
    public void sendInvalidTypeTest() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, "bearer token=\"adfasfaf\"");

        AuthenticationResponse response = context.authenticate();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.statusCode().orElse(200), is(401));
    }

    @Test
    public void sendInvalidBasicTest() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, "basic wrong_header_value");
        AuthenticationResponse response = context.authenticate();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.statusCode().orElse(200), is(401));

        setHeader(context,
                  HttpBasicAuthProvider.HEADER_AUTHENTICATION,
                  "basic " + Base64.getEncoder().encodeToString("Hello".getBytes(StandardCharsets.UTF_8)));

        response = context.authenticate();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.statusCode().orElse(200), is(401));
    }

    @Test
    public void sendDigestHeaderToBasicTest() {
        setHeader(context,
                  HttpBasicAuthProvider.HEADER_AUTHENTICATION,
                  "digest username=\"jack\", realm=\"mic\", nonce=\"nonce\"");
        AuthenticationResponse response = context.authenticate();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.statusCode().orElse(200), is(401));
    }

    @Test
    public void basicTestJill() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, buildBasic("jill", "password"));
        AuthenticationResponse response = context.authenticate();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.statusCode().orElse(200), is(200));

        assertThat(getUsername(context), is("jill"));
        assertThat(context.isUserInRole("admin"), is(false));
        assertThat(context.isUserInRole("user"), is(true));
    }

    private void setHeader(SecurityContext context, String name, String value) {
        context.env(context.env()
                            .derive()
                            .header(name, value)
                            .build());
    }

    private String getUsername(SecurityContext context) {
        return context.user().map(Subject::principal).map(Principal::getName).orElse(null);
    }

    private String buildBasic(String user, String password) {
        return "basic " + Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
