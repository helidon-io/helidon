/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security;

import java.net.URI;
import java.util.Optional;

import io.helidon.security.providers.ProviderForTesting;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Main unit test for {@link Security}.
 */
public class SecurityTest {
    /**
     * System subject.
     * A special subject declaring that this is run "as system", rather under user's
     * or service's subject.
     */
    public static final Subject SYSTEM = Subject.builder()
            .principal(Principal.create("<SYSTEM>"))
            .build();

    private static Security security;
    private static ProviderForTesting firstProvider;
    private static ProviderForTesting secondProvider;

    @BeforeAll
    public static void init() {
        firstProvider = new ProviderForTesting("DENY");
        secondProvider = new ProviderForTesting("SECOND_DENY");
        security = Security.builder()
                .addProvider(firstProvider, "FirstInstance")
                .addProvider(secondProvider, "SecondInstance")
                .authenticationProvider(firstProvider)
                .authorizationProvider(secondProvider)
                .disableTracing()
                .build();
    }

    @Test
    public void testSecurityProviderAuthn() {
        SecurityContext context = security.contextBuilder("unitTest")
                .env(SecurityEnvironment.builder(security.serverTime())
                             .method("GET")
                             .path("/test")
                             .targetUri(URI.create("http://localhost/test"))
                             .addAttribute("resourceType", "TEST")
                             .build())
                .build();

        assertThat(context.isAuthenticated(), is(false));

        AuthenticationResponse authenticate = context.authenticate();

        // current thread should have the correct subject
        assertThat(context.isAuthenticated(), is(true));
        assertThat(authenticate.user(), is(Optional.of(SYSTEM)));

        // should work from context
        assertThat(context.user(), is(Optional.of(SYSTEM)));

        context.runAs(SecurityContext.ANONYMOUS, () -> assertThat(context.isAuthenticated(), is(false)));

        // and correct once again
        assertThat(context.user(), is(Optional.of(SYSTEM)));
    }

    @Test
    public void testSecurityProviderAuthz() {
        SecurityContext context = security.contextBuilder("unitTest").build();

        SecurityEnvironment.Builder envBuilder = context.env().derive()
                .method("GET")
                .path("/test")
                .targetUri(URI.create("http://localhost/test"));

        context.env(envBuilder.addAttribute("resourceType", "SECOND_DENY"));

        // default authorizationClient
        AuthorizationResponse response = context.atzClientBuilder().buildAndGet();

        assertThat(response.status().isSuccess(), is(false));

        context.env(envBuilder.addAttribute("resourceType", "PERMIT"));
        response = context.atzClientBuilder().buildAndGet();

        assertThat(response.status().isSuccess(), is(true));

        // non-default authorizationClient
        context.env(envBuilder.addAttribute("resourceType", "DENY"));
        response = context.atzClientBuilder()
                .explicitProvider("FirstInstance")
                .buildAndGet();

        assertThat(response.status().isSuccess(), is(false));

        context.env(envBuilder.addAttribute("resourceType", "SECOND_DENY"));
        response = context.atzClientBuilder()
                .explicitProvider("FirstInstance")
                .buildAndGet();

        assertThat(response.status().isSuccess(), is(true));
    }
}
